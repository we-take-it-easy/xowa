/*
XOWA: the XOWA Offline Wiki Application
Copyright (C) 2012-2017 gnosygnu@gmail.com

XOWA is licensed under the terms of the General Public License (GPL) Version 3,
or alternatively under the terms of the Apache License Version 2.0.

You may use XOWA according to either of these licenses as is most appropriate
for your project on a case-by-case basis.

The terms of each license can be found in the source code repository:

GPLv3 License: https://github.com/gnosygnu/xowa/blob/master/LICENSE-GPLv3.txt
Apache License: https://github.com/gnosygnu/xowa/blob/master/LICENSE-APACHE2.txt
*/
package gplx.xowa.guis.views;

import cn.edu.ruc.xowa.log.action.Action;
import cn.edu.ruc.xowa.log.action.LoadPageAction;
import cn.edu.ruc.xowa.log.graph.GraphBuilder;
import cn.edu.ruc.xowa.log.page.Page;
import cn.edu.ruc.xowa.log.page.PageCache;
import cn.edu.ruc.xowa.log.page.Url;
import cn.edu.ruc.xowa.log.page.UrlType;
import gplx.*;
import gplx.core.primitives.String_obj_ref;
import gplx.gfui.controls.elems.GfuiElemKeys;
import gplx.gfui.controls.standards.Gfui_html;
import gplx.gfui.kits.core.Gfui_kit;
import gplx.xowa.Xoae_app;
import gplx.xowa.Xoae_page;
import gplx.xowa.guis.Xoa_gui_mgr;
import gplx.xowa.guis.cbks.js.Xog_js_wkr;
import gplx.xowa.guis.menus.Xog_popup_mnu_mgr;
import gplx.xowa.guis.menus.dom.Xog_mnu_grp;
import gplx.xowa.htmls.Xoh_page_html_source;
import gplx.xowa.htmls.heads.Xoh_head_mgr;
import gplx.xowa.htmls.hrefs.Xoh_href_gui_utl;
import gplx.xowa.htmls.js.Xoh_js_cbk;
import gplx.xowa.wikis.pages.Xopg_page_;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

import java.util.regex.Pattern;

public class Xog_html_itm implements Xog_js_wkr, Gfo_invk, Gfo_evt_itm, Xoh_page_html_source {
	private Xoae_app app; private final    Object thread_lock = new Object();
	private final    String_obj_ref scroll_top = String_obj_ref.null_(), node_path = String_obj_ref.null_();
	protected Xog_html_itm() {}	// TEST: for prefs_mgr
	public Xog_html_itm(Xog_tab_itm owner_tab) {
		this.owner_tab = owner_tab;
		app = owner_tab.Tab_mgr().Win().App();
		js_cbk = new Xoh_js_cbk(this);
		Gfui_kit kit = owner_tab.Tab_mgr().Win().Kit();
		cmd_sync = kit.New_cmd_sync(this);	// NOTE: always use sync; async will cause some images to be "lost" in update;
		cmd_async = kit.New_cmd_async(this);
		ev_mgr = new Gfo_evt_mgr(this);
	}
	public Gfo_evt_mgr		Evt_mgr() {return ev_mgr;} private Gfo_evt_mgr ev_mgr;
	public Xog_tab_itm		Owner_tab() {return owner_tab;} private Xog_tab_itm owner_tab;
	public Gfui_html		Html_box() {return html_box;} private Gfui_html html_box;
	public Xoh_js_cbk		Js_cbk() {return js_cbk;} private Xoh_js_cbk js_cbk;
	public Gfo_invk			Cmd_sync() {return cmd_sync;} private Gfo_invk cmd_sync;
	public Gfo_invk			Cmd_async() {return cmd_async;} private Gfo_invk cmd_async; 
	public void Switch_mem(Xog_html_itm comp) {
		Xog_tab_itm temp_owner_tab = owner_tab;		// NOTE: reparent owner_tab, since owner_tab will be switching its html_itm
		this.owner_tab = comp.owner_tab;
		comp.owner_tab = temp_owner_tab;
	}
	public void Html_box_(Gfui_html html_box) {
		this.html_box = html_box;
		html_box.Html_js_cbks_add("xowa_exec", js_cbk);
		Gfo_evt_mgr_.Sub_same(html_box, Gfui_html.Evt_zoom_changed, this);
	}
	public byte[] Get_page_html() {
		return Bry_.new_u8(html_box.Text());
	}
	public String Html_selected_get_src_or_empty()			{return html_box.Html_js_eval_proc_as_str(Xog_js_procs.Selection__get_src_or_empty);}
	public String Html_selected_get_href_or_text()			{return Html_extract_text(html_box.Html_js_eval_proc_as_str(Xog_js_procs.Selection__get_href_or_text));}
	public String Html_selected_get_text_or_href()			{return Html_extract_text(html_box.Html_js_eval_proc_as_str(Xog_js_procs.Selection__get_text_or_href));}
	public String Html_selected_get_active_or_selection()	{return Html_extract_text(html_box.Html_js_eval_proc_as_str(Xog_js_procs.Selection__get_active_or_selection));}
	private String Html_extract_text(String v) {
		if (v == null) return "";	// NOTE: Selection__get_text_or_href never gets called on blank hdoc, which is what happens for Special:XowaDefaultTab; DATE:2015-07-09
		Xoae_page page = owner_tab.Page();
		String site = owner_tab.Wiki().Domain_str();
		String ttl = String_.new_u8(page.Ttl().Full_db());
		return Xoh_href_gui_utl.Html_extract_text(site, ttl, v);
	}

	public static int UnsubmittedShellNum = 0;

	public void Show(Xoae_page page) {
		byte view_mode = owner_tab.View_mode();			
		byte[] html_src = page.Wikie().Html_mgr().Page_wtr_mgr().Gen(page, this, view_mode);	// NOTE: must use wiki of page, not of owner tab; DATE:2015-03-05
		//System.out.println();
		//System.out.println("Xog_html_itm.Show()");
		//System.out.println("current page: " + page.Url().To_str());
		// System.out.println("get html content of: " + new String(html_src));
		//System.out.println("get html content of: " + page.Url().To_str());
		// set the html and links for the page here.
		Url url1 = new Url(page.Url().To_str());
		Page page1 = PageCache.getInstance().getPage(url1);
		System.out.println("3. get html for: " + url1.getFull());
		if (page1 == null)
		{
			System.err.println("Xog_html_itm.Show(): page is null");
		}
		page1.setHtml(new String(html_src));
		UrlType urlType = page1.getUrl().getType();
		if (urlType != UrlType.NONE)
		{
			if (GraphBuilder.getInstance().isStarted())
			{
				Action loadPageAction = new LoadPageAction(page1);
				loadPageAction.perform();

				//加一个step RECORDER
				Shell messageShell = new Shell(SWT.ON_TOP);
				messageShell.setText("Step INFO-NEED Recorder");
				FormLayout layout = new FormLayout();
				layout.spacing = 6;
				layout.marginHeight = layout.marginWidth = 9;
				messageShell.setLayout(layout);

				Text searched = new Text(messageShell, SWT.READ_ONLY|SWT.WRAP);
				searched.setText("Q4: Did you start this step by searching a keyword?");
				FormData searchedData = new FormData();
				searchedData.top = new FormAttachment(0);
				searchedData.left = new FormAttachment(0);
				searchedData.right = new FormAttachment(100);
				searched.setLayoutData(searchedData);

				Group groupSearched = new Group(messageShell, SWT.NONE);
				FormData groupSearchedData = new FormData();
				groupSearchedData.top= new FormAttachment(searched);
				groupSearchedData.left=new FormAttachment(0);
				groupSearchedData.right=new FormAttachment(60);
				groupSearched.setLayoutData(groupSearchedData);
				groupSearched.setLayout(new FillLayout());

				Button searchedYes = new Button(groupSearched, SWT.RADIO);
				searchedYes.setText("yes");
				searchedYes.setSelection(false);

				Button searchedNo = new Button(groupSearched, SWT.RADIO);
				searchedNo.setText("no");
				searchedNo.setSelection(false);


				Text stepInfoNeed = new Text(messageShell, SWT.WRAP | SWT.READ_ONLY);
				stepInfoNeed.setText("Q5: You come to the current step(issued a new query/chained a link on the previous page) because ...");
				FormData stepData = new FormData();
				stepData.top = new FormAttachment(groupSearched);
				stepData.left = new FormAttachment(0);
				stepData.right = new FormAttachment(100);
				stepInfoNeed.setLayoutData(stepData);

				Group groupQ4 = new Group(messageShell, SWT.NONE);
				FormData groupQ4Data = new FormData();
				groupQ4Data.top= new FormAttachment(stepInfoNeed);
				groupQ4Data.left=new FormAttachment(0);
				groupQ4Data.right=new FormAttachment(100);
				groupQ4.setLayoutData(groupQ4Data);
				groupQ4.setLayout(new RowLayout());

				Button radio1 = new Button(groupQ4, SWT.RADIO);
				radio1.setText("I want to look for a specified information to better understand a result. ");
				radio1.setSelection(false);

				Button radio2 = new Button(groupQ4, SWT.RADIO|SWT.WRAP);
				radio2.setText("I want to start a new search direction that I was inspired by some points \nbefore serendipitously.");
				radio2.setSelection(false);

				Button radio3 = new Button(groupQ4, SWT.RADIO);
				radio3.setText("Other information needs that I think will fall under this category. ");
				radio3.setSelection(false);

				Text q4 = new Text(messageShell, SWT.READ_ONLY|SWT.WRAP);
				q4.setText("Input the current question in your mind.");
				FormData q4Data1 = new FormData();
				q4Data1.top = new FormAttachment(groupQ4);
				q4Data1.left = new FormAttachment(0);
				q4Data1.right = new FormAttachment(100);
				q4.setLayoutData(q4Data1);

				Text q4Input = new Text(messageShell, SWT.WRAP|SWT.BORDER);
				FormData q4IData1 = new FormData();
				q4IData1.top = new FormAttachment(q4);
				q4IData1.left = new FormAttachment(0);
				q4IData1.height = 20;
				q4IData1.right = new FormAttachment(100);
				q4Input.setLayoutData(q4IData1);

				//Q5
				Text certainty = new Text(messageShell, SWT.READ_ONLY|SWT.WRAP);
				certainty.setText("Q6: Your confidence (%):");
				FormData txtData1 = new FormData();
				txtData1.top = new FormAttachment(q4Input);
				txtData1.left = new FormAttachment(0);
				txtData1.right = new FormAttachment(36);
				certainty.setLayoutData(txtData1);
				Text certainInput = new Text(messageShell, SWT.BORDER);
				FormData txtData2 = new FormData();
				txtData2.top = new FormAttachment(q4Input);
				txtData2.left = new FormAttachment(certainty);
				certainInput.setLayoutData(txtData2);

				//Q6
				Text changed = new Text(messageShell, SWT.READ_ONLY|SWT.WRAP);
				changed.setText("Q7: Did your information need change in this step?");
				FormData txtData = new FormData();
				txtData.top = new FormAttachment(certainInput);
				txtData.left = new FormAttachment(0);
				txtData.right = new FormAttachment(100);
				changed.setLayoutData(txtData);

				Group groupQ6 = new Group(messageShell, SWT.NONE);
				FormData groupQ6Data = new FormData();
				groupQ6Data.top= new FormAttachment(changed);
				groupQ6Data.left=new FormAttachment(0);
				groupQ6Data.right=new FormAttachment(60);
				groupQ6.setLayoutData(groupQ6Data);
				groupQ6.setLayout(new FillLayout());

				Button changedYes = new Button(groupQ6, SWT.RADIO);
				changedYes.setText("yes");
				changedYes.setSelection(false);

				Button changedNo = new Button(groupQ6, SWT.RADIO);
				changedNo.setText("no");
				changedNo.setSelection(false);

				Button submit = new Button(messageShell, SWT.ABORT);
				submit.setText("Submit");
				FormData subData = new FormData();
				subData.top = new FormAttachment(groupQ6);
				subData.left = new FormAttachment(70);
				submit.setLayoutData(subData);

				Label message = new Label(messageShell, SWT.WRAP);
				FormData messageData = new FormData();
				messageData.top = new FormAttachment(submit);
				messageData.left = new FormAttachment(0);
				messageData.right = new FormAttachment(100);
				message.setLayoutData(messageData);
				message.setForeground(message.getDisplay().getSystemColor(SWT.COLOR_RED));

				submit.addSelectionListener(new SelectionAdapter()
				{
					@Override
					public void widgetSelected(SelectionEvent selectionEvent)
					{
						String description = null;
						String curQus = null;
						String changed = null;
						boolean searched = false;

						if (searchedYes.getSelection())
						{
							searched = true;
						}
						else if (searchedNo.getSelection())
						{
							searched = false;
						}
						else
						{
							message.setText("Error: no option is selected for Q0.");
							return;
						}

						if (radio1.getSelection())
						{
							description = radio1.getText();
						}
						else if (radio2.getSelection())
						{
							description = radio2.getText();
						}
						else if (radio3.getSelection())
						{
							description = radio3.getText();
						}
						else
						{
							message.setText("Error: no option is selected for Q4.");
							return;
						}

						curQus = q4Input.getText();
						if (curQus == null || curQus.isEmpty())
						{
							message.setText("Error: please input your question in the text box of Q4.");
							return;
						}

						String text = certainInput.getText();
						Pattern pattern = Pattern.compile("[0-9]*");
						int certainty = -1;
						if (text != null && !text.isEmpty() && pattern.matcher(text).matches())
						{
							certainty = Integer.parseInt(text);
						}
						else
						{
							message.setText("Error: please input an integer (0-100) in the text box of Q5.");
							return;
						}

						if (changedYes.getSelection())
						{
							changed = "yes";
						}else if(changedNo.getSelection())
						{
							changed = "no";
						}
						else
						{
							message.setText("Error: no option is selected for Q6.");
							return;
						}

						GraphBuilder.getInstance().saveStepQuestion(curQus, description, certainty, changed, searched);
						messageShell.close();
						UnsubmittedShellNum--;
					}
				});

				if (UnsubmittedShellNum == 0)
				{
					UnsubmittedShellNum ++;
				}
				else
				{
					UnsubmittedShellNum ++;
					message.setText("Error: " + UnsubmittedShellNum + " forms of (Q4,Q5,Q6) not been submitted. Submit before continue!");
				}

				messageShell.setLocation(0,0);
				messageShell.setSize(660, 460);
				messageShell.open();
			}
			else
			{
				System.err.println("Please begin a session.");
			}
		}
		else
		{
			System.err.println("Xog_html_itm.Show(): page url is none");
		}
		// do not evict page, this loaded page may be used by the backward and forward actions.
		//PageCache.getInstance().evictPage(url1);

		Html_src_(page, html_src);
		if (view_mode == Xopg_page_.Tid_read){						// used only for Xosrh test; DATE:2014-01-29
			html_box.Html_js_eval_proc_as_str(Xog_js_procs.Win__focus_body);	// NOTE: only focus if read so up / down will scroll box; edit / html should focus edit-box; DATE:2014-06-05
			page.Root().Data_htm_(html_src);
		}
	}
	private void Html_src_(Xoae_page page, byte[] html_bry) {
		String html_str = String_.new_u8(html_bry);
		if (owner_tab.Tab_mgr().Page_load_mode_is_url()) {
			Io_url html_url = app.Usere().Fsys_mgr().App_temp_html_dir().GenSubFil_ary(owner_tab.Tab_key(), ".html");
			try {html_box.Html_doc_html_load_by_url(html_url, html_str);}
			catch (Exception e) {
				app.Usr_dlg().Warn_many("", "", "failed to write html to file; writing directly by memory: page=~{0} file=~{1} err=~{2}", page.Url().To_str(), html_url.Raw(), Err_.Message_gplx_full(e));
				html_box.Html_doc_html_load_by_mem(html_str);
			}
		}
		else
			html_box.Html_doc_html_load_by_mem(html_str);
	}
	public void Html_swap(Xog_html_itm trg_itm) {
		Xog_html_itm src_itm = this;
		Gfui_html src_html = html_box;
		Gfui_html trg_html = trg_itm.html_box;
		Xoh_js_cbk src_js_cbk = js_cbk;
		Xoh_js_cbk trg_js_cbk = trg_itm.js_cbk;
		src_itm.html_box = trg_html;
		trg_itm.html_box = src_html;
		src_itm.js_cbk = trg_js_cbk;
		trg_itm.js_cbk = src_js_cbk;
	}
	public byte[] Get_elem_value_for_edit_box_as_bry()	{return Bry_.new_u8(this.Get_elem_value_for_edit_box());}
	public String Get_elem_value_for_edit_box()			{return Html_elem_atr_get_str(Elem_id__xowa_edit_data_box, Gfui_html.Atr_value);}
	public String Get_elem_value(String elem_id)		{return Html_elem_atr_get_str(elem_id, Gfui_html.Atr_value);}
	public void Html_img_update(String elem_id, String elem_src, int elem_width, int elem_height) {
		GfoMsg m = GfoMsg_.new_cast_(Invk_html_img_update).Add("elem_id", elem_id).Add("elem_src", elem_src).Add("elem_width", elem_width).Add("elem_height", elem_height);
		Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_img_update, m);
	}
	public void Html_elem_delete(String elem_id) {
		GfoMsg m = GfoMsg_.new_cast_(Invk_html_elem_delete).Add("elem_id", elem_id);
		Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_elem_delete, m);
	}
	@gplx.Virtual public String	Html_elem_atr_get_str(String id, String atr_key)		{return html_box.Html_js_eval_proc_as_str(Xog_js_procs.Doc__atr_get_as_obj, id, atr_key);}
	@gplx.Virtual public boolean		Html_elem_atr_get_bool(String id, String atr_key)		{return Bool_.Parse(html_box.Html_js_eval_proc_as_str(Xog_js_procs.Doc__atr_get_to_str, id, atr_key));}
	

	public void Html_atr_set(String elem_id, String atr_key, String atr_val) {
		synchronized (thread_lock) {	// needed for Special:Search and async cancel; DATE:2015-05-02
			GfoMsg m = GfoMsg_.new_cast_(Invk_html_elem_atr_set).Add("elem_id", elem_id).Add("atr_key", atr_key).Add("atr_val", atr_val);
			Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_elem_atr_set, m);
		}
	}
	public void Html_redlink(String html_uid) {Html_doc_atr_append_or_set(html_uid, "class", gplx.xowa.wikis.pages.lnkis.Xoh_redlink_utl.New_str);}
	private void Html_doc_atr_append_or_set(String elem_id, String atr_key, String atr_val) {
		GfoMsg m = GfoMsg_.new_cast_(Invk_html_doc_atr_append_or_set).Add("elem_id", elem_id).Add("atr_key", atr_key).Add("atr_val", atr_val);
		Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_doc_atr_append_or_set, m);
	}
	public void Html_elem_replace_html(String id, String html) {
		synchronized (thread_lock) {	// needed for Special:Search and async; DATE:2015-04-23
			GfoMsg m = GfoMsg_.new_cast_(Invk_html_elem_replace_html).Add("id", id).Add("html", html);
			Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_elem_replace_html, m);
		}
	}
	public void Html_elem_append_above(String id, String html) {
		synchronized (thread_lock) {	// needed for Special:Search and async; DATE:2015-04-23
			GfoMsg m = GfoMsg_.new_cast_(Invk_html_elem_append_above).Add("id", id).Add("html", html);
			Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_elem_append_above, m);
		}
	}
	public void Html_gallery_packed_exec() {
		if (!String_.Eq(owner_tab.Tab_key(), owner_tab.Tab_mgr().Active_tab().Tab_key())) return;	// do not exec unless active;
		GfoMsg m = GfoMsg_.new_cast_(Invk_html_gallery_packed_exec);
		Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_gallery_packed_exec, m);
		module_packed_done = true;
	}
	public void Html_popups_bind_hover_to_doc() {
		if (!String_.Eq(owner_tab.Tab_key(), owner_tab.Tab_mgr().Active_tab().Tab_key())) return;	// do not exec unless active;
		GfoMsg m = GfoMsg_.new_cast_(Invk_html_popups_bind_hover_to_doc);
		Gfo_invk_.Invk_by_msg(cmd_sync, Invk_html_popups_bind_hover_to_doc, m);
		module_popups_done = true;
	}
	private boolean module_packed_done = false, module_popups_done = false;
	public void Tab_selected(Xoae_page page) {
		Xoh_head_mgr module_mgr = page.Html_data().Head_mgr();
		if (module_mgr.Itm__gallery().Enabled() && !module_packed_done)
			this.Html_gallery_packed_exec();
		if (module_mgr.Itm__popups().Enabled() && !module_popups_done)
			this.Html_popups_bind_hover_to_doc();
	}
	public void Scroll_page_by_bmk_gui()	{Gfo_invk_.Invk_by_key(cmd_async, Invk_scroll_page_by_bmk);}
	private void Scroll_page_by_bmk() {
		if (!String_.Eq(owner_tab.Tab_key(), owner_tab.Tab_mgr().Active_tab().Tab_key())) return; // only set html page position on active tab; otherwise, page "scrolls down" mysteriously on unseen tab; DATE:2014-05-02
		String html_doc_pos = owner_tab.Page().Html_data().Bmk_pos();
		if (html_doc_pos == null) {
			String auto_focus_id = app.Gui_mgr().Html_mgr().Auto_focus_id();
			if (String_.Len_eq_0(auto_focus_id)) return;					// don't focus anything
			if (String_.Eq(auto_focus_id, " first_anchor"))					// NOTE: HTML 4/5 do not allow space as id; XOWA using space here to create a unique_key that will never collide with any id
				html_box.Html_js_eval_proc_as_str(Xog_js_procs.Win__focus_body);	// NOTE: will focus body if content-editable, else first_anchor
			else
				html_box.Html_js_eval_proc_as_str(Xog_js_procs.Doc__elem_focus, auto_focus_id);
		}
		else if (String_.Eq(html_doc_pos, gplx.xowa.guis.history.Xog_history_itm.Html_doc_pos_toc))	// NOTE: special case to handle TOC clicks; DATE:2013-07-17
			Scroll_page_by_id("toc");
		else {
			Html_window_vpos_parse(html_doc_pos, scroll_top, node_path);
			html_box.Html_js_eval_proc_as_str(Xog_js_procs.Win__vpos_set, node_path.Val(), scroll_top.Val());
		}
	}
	public void Scroll_page_by_id_gui(String id)	{Gfo_invk_.Invk_by_val(cmd_async, Invk_scroll_page_by_id, id);}
	private boolean Scroll_page_by_id(String id) {
		return (id == null) 
			? false
			: html_box.Html_js_eval_proc_as_bool(Xog_js_procs.Win__scroll_elem_into_view, gplx.langs.htmls.encoders.Gfo_url_encoder_.Id.Encode_str(id));
	}
	public void Js_enabled_(boolean v) {
		html_box.Html_js_enabled_(v);
	}
	private void When_menu_detected() {
		Xoa_gui_mgr gui_mgr = app.Gui_mgr(); Gfui_kit kit = gui_mgr.Kit();
		Xog_popup_mnu_mgr popup_mnu_mgr = gui_mgr.Menu_mgr().Popup();
		Xog_mnu_grp popup_mnu = popup_mnu_mgr.Html_page();
		if		(String_.Len_gt_0(this.Html_selected_get_src_or_empty()))
			popup_mnu = popup_mnu_mgr.Html_file();
		else if (String_.Len_gt_0(this.Html_selected_get_text_or_href()))
			popup_mnu = popup_mnu_mgr.Html_link();
		kit.Set_mnu_popup(html_box, popup_mnu.Under_mnu());
	}
	private void When_zoom_changed(boolean clicks_is_positive) {
		app.Api_root().Gui().Font().Adj(clicks_is_positive ? 1 : -1);
	}
	public Object Invk(GfsCtx ctx, int ikey, String k, GfoMsg m) {
		if		(ctx.Match(k, Invk_html_img_update))					html_box.Html_js_eval_proc_as_bool	(Xog_js_procs.Doc__elem_img_update		, m.ReadStr("elem_id"), m.ReadStr("elem_src"), m.ReadInt("elem_width"), m.ReadInt("elem_height"));
		else if	(ctx.Match(k, Invk_html_elem_atr_set))					html_box.Html_js_eval_proc_as_str	(Xog_js_procs.Doc__atr_set				, m.ReadStr("elem_id"), m.ReadStr("atr_key"), m.ReadStr("atr_val"));
		else if	(ctx.Match(k, Invk_html_doc_atr_append_or_set))			html_box.Html_js_eval_proc_as_str	(Xog_js_procs.Doc__atr_append_or_set	, m.ReadStr("elem_id"), m.ReadStr("atr_key"), m.ReadStr("atr_val"));
		else if	(ctx.Match(k, Invk_html_elem_delete))					html_box.Html_js_eval_proc_as_bool	(Xog_js_procs.Doc__elem_delete			, m.ReadStr("elem_id"));
		else if	(ctx.Match(k, Invk_html_elem_replace_html))				html_box.Html_js_eval_proc_as_str	(Xog_js_procs.Doc__elem_replace_html	, m.ReadStr("id"), m.ReadStr("html"));
		else if	(ctx.Match(k, Invk_html_elem_append_above))				html_box.Html_js_eval_proc_as_str	(Xog_js_procs.Doc__elem_append_above	, m.ReadStr("id"), m.ReadStr("html"));
		else if	(ctx.Match(k, Invk_html_gallery_packed_exec))			html_box.Html_js_eval_proc_as_str	(Xog_js_procs.Xtn__gallery_packed_exec);
		else if	(ctx.Match(k, Invk_html_popups_bind_hover_to_doc))		html_box.Html_js_eval_script("xowa_popups_bind_doc();");
		else if (ctx.Match(k, Invk_scroll_page_by_bmk))					Scroll_page_by_bmk();
		else if (ctx.Match(k, Invk_scroll_page_by_id))					Scroll_page_by_id(m.ReadStr("v"));
		else if (ctx.Match(k, Invk_html_elem_focus))					html_box.Html_js_eval_proc_as_str(Xog_js_procs.Doc__elem_focus, m.ReadStr("v"));
		else if (ctx.Match(k, GfuiElemKeys.Evt_menu_detected))			When_menu_detected();
		else if	(ctx.Match(k, Gfui_html.Evt_zoom_changed))				When_zoom_changed(m.ReadBool("clicks_is_positive"));
		else	return Gfo_invk_.Rv_unhandled;
		return this;
	}
	private static final String 
	  Invk_html_gallery_packed_exec = "html_gallery_packed_exec", Invk_html_popups_bind_hover_to_doc = "html_popups_bind_hover_to_doc"
	, Invk_html_img_update = "html_img_update", Invk_html_elem_atr_set = "html_elem_atr_set"
	, Invk_html_doc_atr_append_or_set = "html_doc_atr_append_or_set", Invk_html_elem_delete = "html_elem_delete", Invk_html_elem_replace_html = "html_elem_replace_html", Invk_html_elem_append_above = "html_elem_append_above"
	, Invk_scroll_page_by_bmk = "scroll_page_by_bmk", Invk_scroll_page_by_id = "scroll_page_by_id"
	;
	public static final String 
	  Elem_id__xowa_edit_data_box		= "xowa_edit_data_box"
	, Elem_id__xowa_edit_rename_box		= "xowa_edit_rename_box"
	, Elem_id__first_heading			= "firstHeading"
	, Invk_html_elem_focus				= "html_elem_focus"
	;
	public static void Html_window_vpos_parse(String v, String_obj_ref scroll_top, String_obj_ref node_path) {
		int pipe_pos = String_.FindFwd(v, "|"); if (pipe_pos == String_.Find_none) return; // if elem_get_path returns invalid value, don't fail; DATE:2014-04-05
		scroll_top.Val_(String_.Mid(v, 0, pipe_pos));
		String node_path_val = String_.Mid(v, pipe_pos + 1, String_.Len(v));
		node_path_val = "'" + String_.Replace(node_path_val, ",", "','") + "'";
		node_path.Val_(node_path_val);
	}
}
