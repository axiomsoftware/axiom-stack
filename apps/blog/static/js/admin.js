// General

function select_all() {
	$("input.cb").attr("checked",true);
}

function unselect_all() {
	$("input.cb").attr("checked",false);
}


// Create and Edit Posts Tabs

function validate_post() {
	if ($("#title").val() == "" || FCKeditorAPI.GetInstance("body").GetXHTML == "") {
		$("#validation").text("Posts require both a Title and a Body.").show();
		$("#success").hide();
		return false;
	}
	if (!$("#title").val().match(/\w/g)) {
		$("#validation").text("The Post Title must have at least one Alpha-Numeric Character.").show();
		$("#success").hide();
		return false;
	}
	return true;
}

function clear_post_title(field) {
	if (field.value == "Enter Post Title") {
		field.value = "";
	}
}

function populate_post_title(field) {
	if (field.value == "") {
		field.value = "Enter Post Title";
	}
}


// Manage Posts Screen

function toggle_comment(link,id) {
	if (link.innerHTML == "View Comments") {
		link.innerHTML = "Hide Comments";
		$("#"+id).show(300);
	} else {
		link.innerHTML = "View Comments";
		$("#"+id).hide(300);
	}
}


function approve_comment(id) {
	var cb = function(data,text) {
		if (data.success) {
			$("#approve_" + id).remove();
			$("#message_" + id).text("This Comment has been Approved.").addClass("success");
		}
	}

	$.post(comments_url, {approveid: id}, cb, "json");
}

function delete_comment(id) {
	var cb = function(data,text) {
		if (data.success) {
			$("#comment_" + id).replaceWith("<div class=\"message error\">This Comment has been Deleted.</div>");
		}
	}

	$.post(comments_url, {deleteid: id}, cb, "json");
}

// Manage Whitelist Screen

function validate_whitelist() {
	if ($("#openid_identity").val() == "") {
		$("#validation").text("Please enter an OpenID Account to add.").show();
		$("#success").hide();
		$("#error").hide();
		return false;
	}
	return true;
}

// Manage Categories Screen

function validate_category() {
	if ($("#name").val() == "") {
		$("#validation").text("Please enter a Category Title.").show();
		$("#success").hide();
		$("#error").hide()
		return false;
	}
	if (!$("#name").val().match(/\w/g)) {
		$("#validation").text("The Category Title must have at least one Alpha-Numeric Character.").show();
		$("#success").hide();
		$("#error").hide()
		return false;
	}
	return true;
}


// Manage Authors Screen

function validate_new_author() {
	var username = $("#username").val();
	var fullname = $("#fullname").val();
	var email = $("#email").val();
	var password1 = $("#password1").val();
	var password2 = $("#password2").val();
	var role = $("#role").val();
	if (username == "" || fullname == "" || email == "" || password1 == "" || password2 == ""|| role == "") {
		$("#validation").text("Please fill out all fields below.").show();
		$("#success").hide();
		return false;
	}
	for (var i = 0; i < invalid_usernames.length; i++) {
		if (username == invalid_usernames[i]) {
			$("#validation").text("This Username is already in use.  Please try another.").show();
			$("#success").hide();
			return false;
		}
	}
	for (var i = 0; i < invalid_emails.length; i++) {
		if (email == invalid_emails[i]) {
			$("#validation").text("This Email Address is already in use.  Please use another.").show();
			$("#success").hide();
			return false;
		}
	}
	var email_regex = /^([a-zA-Z0-9_\.\-])+\@(([a-zA-Z0-9\-])+\.)+([a-zA-Z0-9]{2,4})+$/;
	if (!email_regex.test(email)) {
		$("#validation").text("Please enter a valid Email Address before proceeding.").show();
		$("#success").hide();
		return false;
	}
	if (password1 != password2) {
		$("#validation").text("Please make sure the Password fields match before proceeding.").show();
		$("#success").hide();
		return false;				
	}
	if (password1.length < 4) {
		$("#validation").text("Please make sure your Password is at least 4 characters long before proceeding.").show();
		$("#success").hide();
		return false;
	}
	return true;
}

function validate_existing_author() {
	var username = $("#username").val();
	var fullname = $("#fullname").val();
	var email = $("#email").val();
	var password1 = $("#password1").val();
	var password2 = $("#password2").val();
	var role = $("#role").val();
	if (username == "" || fullname == "" || email == "" || role == "") {
		$("#validation").text("Please fill out all fields below.").show();
		$("#success").hide();
		return false;
	}
	for (var i = 0; i < invalid_usernames.length; i++) {
		if (username == invalid_usernames[i]) {
			$("#validation").text("This Username is already in use.  Please try another.").show();
			$("#success").hide();
			return false;
		}
	}
	for (var i = 0; i < invalid_emails.length; i++) {
		if (email == invalid_emails[i]) {
			$("#validation").text("This Email Address is already in use.  Please use another.").show();
			$("#success").hide();
			return false;
		}
	}
	var email_regex = /^([a-zA-Z0-9_\.\-])+\@(([a-zA-Z0-9\-])+\.)+([a-zA-Z0-9]{2,4})+$/;
	if (!email_regex.test(email)) {
		$("#validation").text("Please enter a valid Email Address before proceeding.").show();
		$("#success").hide();
		return false;
	}
	if (password1 || password2) {
		if (password1 != password2) {
			$("#validation").text("Please make sure the Password fields match before proceeding.").show();
			$("#success").hide();
			return false;				
		}
		if (password1.length < 4) {
			$("#validation").text("Please make sure your Password is at least 4 characters long before proceeding.").show();
			$("#success").hide();
			return false;
		}
	}
	return true;
}

function parse_username(field) {
	field.value = field.value.replace(/\s+/g, "_").replace(/\W+/g, "").toLowerCase();
}

// Manage Settings

function validate_settings() {
	if ($("#title").val() == "" || $("#description").val() == "") {
		$("#validation").text("Title and Description are both Required.").show();
		$("#success").hide();
		$("#error").hide();
		return false;
	}
	return true;
}

function hide_wordpress_button(btn) {
	btn.style.display = "none";
	$("#wordpress_msg").show();
}

// Manage Templates

function select_two_col() {
	$("#twocol").addClass("curr");
	$("#threecol").removeClass("curr");
	$("#layout").val("twocol");
}

function select_three_col() {
	$("#twocol").removeClass("curr");
	$("#threecol").addClass("curr");
	$("#layout").val("threecol");
}

function select_style1() {
	$("#style1").addClass("curr");
	$("#style2").removeClass("curr");
	$("#style3").removeClass("curr");
	$("#style").val("style1");
}

function select_style2() {
	$("#style1").removeClass("curr");
	$("#style2").addClass("curr");
	$("#style3").removeClass("curr");
	$("#style").val("style2");
}

function select_style3() {
	$("#style1").removeClass("curr");
	$("#style2").removeClass("curr");
	$("#style3").addClass("curr");
	$("#style").val("style3");
}
