function error(text){
	$('div.error_message').show().text(text);
}

function creation_validate(){
	if(!$("input[name='username']").attr('value')){
		error("Please enter a username.");
		return false;
	}
	if(!$("input[name='password']").attr('value')){
		error("Please enter a password.");
		return false;
	}
	if($("input[name='password']").attr('value') != $("input[name='reenter-password']").attr('value')){
		error("Passwords don't match.");
		return false;
	}
	return true;
}
