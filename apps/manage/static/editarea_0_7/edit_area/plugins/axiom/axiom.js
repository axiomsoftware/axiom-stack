/**
 * Axiom editarea plugin.  Adds shell execution keybinding. Requires jQuery.
 */  
var EditArea_axiom= {
	/**
	 * Is called each time the user touch a keyboard key.
	 *	 
	 * @param (event) e: the keydown event
	 * @return true - pass to next handler in chain, false - stop chain execution
	 * @type boolean	 
	 */
	onkeydown: function(e){
		var str= String.fromCharCode(e.keyCode);
		// desactivate the "f" character
		if(CtrlPressed(e) && str.toLowerCase()=="e"){
			window.parent.executeCode();
			return false;
		}
		return true;
	}
};

// Adds the plugin class to the list of available EditArea plugins
editArea.add_plugin("axiom", EditArea_axiom);
