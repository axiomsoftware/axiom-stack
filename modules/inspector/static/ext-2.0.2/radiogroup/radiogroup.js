/**
 * @author Robert Williams (vtswingkid)
 * @version 1.0.4
 */
Ext.namespace('Ext.ux');
Ext.ux.RadioGroup = Ext.extend(Ext.form.Field,  {
    /**
     * @cfg {String} focusClass The CSS class to use when the checkbox receives focus (defaults to undefined)
     */
    focusClass : undefined,
    /**
     * @cfg {String} fieldClass The default CSS class for the checkbox (defaults to "x-form-field")
     */
    fieldClass: "x-form-field",
    /**
     * @cfg {Boolean} checked True if the the checkbox should render already checked (defaults to false)
     */
    checked: false,
    /**
     * @cfg {String/Object} autoCreate A DomHelper element spec, or true for a default element spec (defaults to
     * {tag: "input", type: "radio", autocomplete: "off"})
     */
    defaultAutoCreate : { tag: "input", type: 'radio', autocomplete: "off"},
    /**
     * @cfg {String} boxLabel The text that appears beside the checkbox
     */

	getId:function(){
		//if multiple radios are defined use this information
		if(this.radios && this.radios instanceof Array){
			if(this.radios.length){
				var r=this.radios[0];
				this.value=r.value;
				this.boxLabel=r.boxLabel;
				this.checked=r.checked || false;
				this.readOnly=r.readOnly || false;
				this.disabled=r.disabled || false;
				this.tabIndex=r.tabIndex;
				this.cls=r.cls;
				this.listeners=r.listeners;
				this.style=r.style;
				this.bodyStyle=r.bodyStyle;
				this.hideParent=r.hideParent;
				this.hidden=r.hidden;
			}
		}
		Ext.ux.RadioGroup.superclass.getId.call(this);
	},

	// private
    initComponent : function(){
        Ext.ux.RadioGroup.superclass.initComponent.call(this);
        this.addEvents(
            /**
             * @event change
             * Fires when the radio value changes.
             * @param {Ext.vx.RadioGroup} this This radio
             * @param {Boolean} checked The new checked value
             */
            'check'
        );
    },

    // private
    onResize : function(){
        Ext.ux.RadioGroup.superclass.onResize.apply(this, arguments);
        if(!this.boxLabel){
            this.el.alignTo(this.wrap, 'c-c');
        }
    },

    // private
    initEvents : function(){
        Ext.ux.RadioGroup.superclass.initEvents.call(this);
        this.el.on("click", this.onClick,  this);
        this.el.on("change", this.onClick,  this);
    },

	// private
    getResizeEl : function(){
        return this.wrap;
    },

    // private
    getPositionEl : function(){
        return this.wrap;
    },

    /**
     * Overridden and disabled. The editor element does not support standard valid/invalid marking. @hide
     * @method
     */
    markInvalid : Ext.emptyFn,
    /**
     * Overridden and disabled. The editor element does not support standard valid/invalid marking. @hide
     * @method
     */
    clearInvalid : Ext.emptyFn,

    // private
    onRender : function(ct, position){
        Ext.ux.RadioGroup.superclass.onRender.call(this, ct, position);
        this.wrap = this.el.wrap({cls: "x-form-check-wrap"});
        if(this.boxLabel){
            this.wrap.createChild({tag: 'label', htmlFor: this.el.id, cls: 'x-form-cb-label', html: this.boxLabel});
        }
		if(!this.isInGroup){
			this.wrap.applyStyles({'padding-top':'2px'});
		}
        if(this.checked){
            this.setChecked(true);
        }else{
            this.checked = this.el.dom.checked;
        }
		if (this.radios && this.radios instanceof Array) {
			this.els=new Array();
			this.els[0]=this.el;
			for(var i=1;i<this.radios.length;i++){
				var r=this.radios[i];
				this.els[i]=new Ext.ux.RadioGroup({
					renderTo:this.wrap,
					hideLabel:true,
					boxLabel:r.boxLabel,
					checked:r.checked || false,
					value:r.value,
					name:this.name || this.id,
					readOnly:r.readOnly || false,
					disabled:r.disabled || false,
					tabIndex:r.tabIndex,
					cls:r.cls,
					listeners:r.listeners,
					style:r.style,
					bodyStyle:r.bodyStyle,
					hideParent:r.hideParent,
					hidden:r.hidden,
					isInGroup:true
				});
				if (this.horizontal) {
					this.els[i].el.up('div.x-form-check-wrap').applyStyles({
						'display': 'inline',
						'padding-left': '5px'
					});
				}
			}
			if(this.hidden)this.hide();
		}
    },

    initValue : function(){
        if(this.value !== undefined){
            this.el.dom.value=this.value;
        }else if(this.el.dom.value.length > 0){
            this.value=this.el.dom.value;
        }
    },

    // private
    onDestroy : function(){
		if (this.radios && this.radios instanceof Array) {
			var cnt = this.radios.length;
			for(var x=1;x<cnt;x++){
				this.els[x].destroy();
			}
		}
        if(this.wrap){
            this.wrap.remove();
        }
        Ext.ux.RadioGroup.superclass.onDestroy.call(this);
    },

	setChecked:function(v){
        if(this.el && this.el.dom){
			var fire = false;
			if(v != this.checked)fire=true;
			this.checked=v;
            this.el.dom.checked = this.checked;
            this.el.dom.defaultChecked = this.checked;
    	    if(fire)this.fireEvent("check", this, this.checked);
	    }
    },
    /**
     * Returns the value of the checked radio.
     * @return {Mixed} value
     */
    getValue : function(){
        if(!this.rendered) {
            return this.value;
        }
        var p=this.el.up('form');//restrict to the form if it is in a form
		if(!p)p=Ext.getBody();
		var c=p.child('input[name='+escape(this.el.dom.name)+']:checked', true);
		return (c)?c.value:this.value;
    },

	// private
    onClick : function(){
        if(this.el.dom.checked != this.checked){
			var p = this.el.up('form');
			if (!p)
				p = Ext.getBody();
			var els = p.select('input[name=' + escape(this.el.dom.name) + ']');
			els.each(function(el){
				if (el.dom.id == this.id) {
					this.setChecked(true);
				}
				else {
					var e = Ext.getCmp(el.dom.id);
					e.setChecked.apply(e, [false]);
				}
			}, this);
        }
    },

    /**
     * Checks the radio box with the matching value
     * @param {Mixed} v
     */

    setValue : function(v){
        if(!this.rendered) {
            this.value=v;
            return;
        }
        var p=this.el.up('form');//restrict to the form if it is in a form
        if(!p)p=Ext.getBody();
        var target = p.child('input[name=' + escape(this.el.dom.name) + '][value=' + v + ']', true);
        if (target) target.checked = true;
    },

	clear: function(){
		if (!this.rendered) return;
		var p = this.el.up('form');//restrict to the form if it is in a form
		if (!p) p = Ext.getBody();
		var c = p.child('input[name=' + escape(this.el.dom.name) + ']:checked', true);
		if (c) c.checked = false;
	},

	disable: function(){
		if (!this.rendered) return;
		var p = this.el.up('form');//restrict to the form if it is in a form
		if (!p) p = Ext.getBody();
		var els = p.select('input[name=' + escape(this.el.dom.name) + ']');
		els.each(function(el){
			if (el.dom.id == this.id) {
				Ext.ux.RadioGroup.superclass.disable.call(this);
			}
			else {
				var e = Ext.getCmp(el.dom.id);
				Ext.ux.RadioGroup.superclass.disable.call(e);
			}
		}, this);
	},

	enable: function(){
		if (!this.rendered) return;
		var p = this.el.up('form');//restrict to the form if it is in a form
		if (!p) p = Ext.getBody();
		var els = p.select('input[name=' + escape(this.el.dom.name) + ']');
		els.each(function(el){
			if (el.dom.id == this.id) {
				Ext.ux.RadioGroup.superclass.enable.call(this);
			}
			else {
				var e = Ext.getCmp(el.dom.id);
				Ext.ux.RadioGroup.superclass.enable.call(e);
			}
		}, this);
	},

	hide: function(){
		if (!this.rendered) return;
		this.wrap.hide();
		this.wrap.parent().parent().hide();
	},

	show: function(){
		if (!this.rendered) return;
		this.wrap.show();
		this.wrap.parent().parent().show();
	}
});
Ext.reg('ux-radiogroup', Ext.ux.RadioGroup);