$(document).ready(function(){
	var f = function(){$(this).toggleClass('hover');};
	$('ul.applist > li').mouseover(f).mouseout(f);
	$('#new_app > input ').keypress(function(e){ if(e.keyCode == 13){createApp();} });
});

function message(data){
	$('#messages').slideDown('slow').html(data);
	setTimeout(function(){$('#messages').slideUp('slow');}, 10000);
}

function home(){
	$('#applist > li').removeClass('active');
	$('#applist > li.first').addClass('active');
	$('#actions').hide();
	$('#home').slideDown();
}

function setApp(name, uri, node){
	appURI = uri;

	$('#actions').slideDown();
	$('#home').hide();

	// toggle active tab
	$('#applist > li').removeClass('active');
	$(node).addClass('active');

	// load stats
	$('#dashboard_stats').html('<div class="section"><img style="padding-left: 36%" src="'+staticMountpoint+'/ajax-loader.gif"/></div>');
	$.ajax({url:         manageURI+'/dashboard_stats',
			cache:       false,
			type:        'post',
			contentType: 'text/json',
			data:        '{app: "'+name+'"}',
			success:     function(data){$('#dashboard_stats').replaceWith(data);}
		   });
}

function createApp(){
	var app = $('#new_app_name').val();
	$('#new_app > a').toggleClass('disabled').html('Creating...');
	$.ajax({url:       manageURI+'/new_application',
			cache:     false,
			type:      'post',
			data:      {app_name: app},
			dataType:  'json',
			success:   function(data, status){
				if(data.success){
					message("Application "+app+" was succesfully created.");
					$('ul.applist').append('<li onclick="setApp(\''+app+'\',\'/'+app+'\', this)">'+app+'</li>');
				} else {
					message(data.message);
				}
				$('#new_app > a').toggleClass('disabled').html('Create');
			},
			error:     function(req, status, error){
				message('Error creating application.');
				$('#new_app > a').toggleClass('disabled').html('Create');
			}
		   });
}

function newApp(){
	$('#new_app').slideDown('slow');
}

function getSelectedApp(){
	return $('ul.applist > li.active').text().replace(/\s+$/,'');
}

function openShell(){
	$('#shell').slideDown('slow');
	editAreaLoader.init({
		id: "commands",
		start_highlight: true,
		allow_resize: "both",
		allow_toggle: false,
		language: "en",
		syntax: "js",
		toolbar: "new_document, |, search, go_to_line, |, undo, redo, |, select_font, |, change_smooth_selection, highlight, reset_highlight, |, help",
		min_height: 100,
		replace_tab_by_spaces: 4,
		plugins: 'axiom'
	});
}

function openTests(){
	window.open(manageURI+'/run_all?app_name='+getSelectedApp(), 'Tests', 'width=1000,height=800,resizable=yes');
}

function appAction(cmd){
	$.ajax({url:         manageURI+'/appAction',
			cache:       false,
			type:        'post',
			success:      function(data){
				message(data);
           	},
			error:        function(data){
				message(data);
			},
			data:         {command:cmd, app: getSelectedApp()}
		   });
}

function executeCode() {
    $.ajax({ url:  "eval_shell?"+(new Date()).getTime(),
			 cache: false,
			 success: function(data) {
				 if (data.match("Exception:")) {
					 var i = data.lastIndexOf(' ');
					 var line_num = data.substring((data.length-(data.length-i)),data.length).replace(/.*\D(\d+).*/, "$1");
					 if (!isNaN(line_num)) {
						 area.go_to_line(line_num);
					 }
				 }
				 $("#output").html(data);
			 },
			 data: {commands: editAreaLoader.getValue("commands"), app_name: getSelectedApp()},
			 type: "post"
		   });
}
