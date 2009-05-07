/*
 * Axiom Stack Web Application Framework
 * Copyright (C) 2008  Axiom Software Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Axiom Software Inc., 11480 Commerce Park Drive, Third Floor, Reston, VA 20191 USA
 * email: info@axiomsoftwareinc.com
 */

/**
 * The global TAL function for rendering TAL/TALE documents.  This is called within
 * <code> AxiomObject.renderTAL() </code>, and thus, would generally not be called
 * directly by the application programmer.
 *
 * @param {XML} taldoc A TAL document as a JavaScript XML object
 * @param {Object} data A JavaScript object containing the data to pass to the TAL rendering
 *                      engine
 * @return {XML} The rendered TAL document as a JavaScript XML object
 */
function TAL(doc, data) {
    var tal = new Namespace('tal', 'http://axiomstack.com/tale');
    TAL.Scope.prototype = data;
    var local_data = new TAL.Scope();
    default xml namespace = doc.namespace('');
    if(doc.namespace('tal')==tal) {
        TAL.TALE(doc, local_data, tal);
    } else {
        tal = new Namespace('tal', 'http://xml.zope.org/namespaces/tal');
        local_data['repeat']={};
        TAL.TAL(doc, local_data, data, tal);
    }

    doc.removeNamespace(tal);
    return doc;
}
TAL.namespace_transform = function(doc, ns_transform){
    for each(node in doc..*){
        for each(namespace in ns_transform){
            var to = namespace.to;
            var from = namespace.from;
            var transforms = node.@from::*;
            if(transforms.length() > 0){
                for each(attr in transforms){
                    node.@to::[attr.name()] = attr.toString();
                    delete node.@from::[attr.name()];
                }
            }
        }
    }
    return doc;
}

TAL.Scope = function () {;}
TAL.terms = function (d, e) {return (new Function('data','with(data) return {'+e+'}')).call(d['this'],d)}
TAL.func = function (d, e) {return (new Function('data', 'with(data) return '+e)).call(d['this'],d)}

TAL.TALE = function (n, data, tal) {
        var tn;
        data.node=n;
        if((tn=n.@tal::repeat).length()) {
                var r = TAL.terms(data, tn);
                delete n.@tal::repeat;
                var p=n.parent();
                for (var l in r) {
                    var collection = r[l];
					if(!collection) continue;
					for (var k in collection){
                        TAL.Scope.prototype = data;
                        var repeat_data = new TAL.Scope();
                        repeat_data[l] = collection[k];
                        if(!repeat_data['repeat']) repeat_data['repeat'] = {}
                        repeat_data['repeat'][l] = new TAL.Repeat(k, collection.length);
                        p.insertChildBefore(n, n.copy());
                        TAL.TALE(p.*[n.childIndex()-1], repeat_data, tal);
                    }
                }
                delete p.*[n.childIndex()];
                return;
        }
        if((tn=n.@tal::['var']).length()) {
                TAL.Scope.prototype = data;
                data = new TAL.Scope();
                var r = TAL.terms(data, tn);
                for(var k in r) data[k]=r[k];
                delete n.@tal::['var'];
        }
        if((tn=n.@tal::eval).length()) {
                TAL.func(data, tn);
                delete n.@tal::eval;
        }
        if((tn=n.@tal::['if']).length()) {
                var r = TAL.func(data, tn);
                 if(r) {
                        delete n.@tal::['if'];
                } else {
                        delete n.parent().*[n.childIndex()];
                        return;
                }
        }
        if((tn=n.@tal::['repeat-content']).length()) {
            delete n.@tal::['repeat-content'];
            n.replace('*', <kill tal:omit="true" tal:repeat={tn} xmlns:tal={tal.uri}>{n.*}</kill>);
        }
       if((tn=n.@tal::['content-if']).length()) {
	   TAL.Scope.prototype = data;
           data = new TAL.Scope();
            var r = TAL.func(data, tn);
            if(r) {
                n.replace('*', r);
            } else {
                delete n.parent().*[n.childIndex()];
            }
            delete n.@tal::['content-if'];
        }
        if((tn=n.@tal::attr).length()) {
                var r = TAL.terms(data, tn);
                for(var k in r)
                    if(r[k]){
                        var ns_match = k.match(/(\w+):(\w+)/);
                        if(ns_match){
                            var ns = n.namespace([ns_match[1]]);
                            n.@ns::[ns_match[2]] = r[k];
                        }
                        else
                            n.@[k]=r[k];
                    }
                delete n.@tal::attr;
        }
        if((tn=n.@tal::content).length()) {
	    TAL.Scope.prototype = data;
            data = new TAL.Scope();
                n.replace('*', TAL.func(data, tn));
                delete n.@tal::content;

        }
        if((tn=n.@tal::replace).length()) {
	    TAL.Scope.prototype = data;
            data = new TAL.Scope();
                n.parent().replace(n.childIndex(), TAL.func(data, tn));
                return;
        }
        if((tn=n.@tal::text).length()) {
                var r=new RegExp('\\'+tn+'\\{([^}]+)\\}|\\'+tn+'(\\w+)','g');
                delete n.@tal::text;
                for each(var t in n.text()){
					var text_str = t.toXMLString();
                    n.replace(t.childIndex(),
                              (/^\s/.test(text_str) ? ' ' : '') + new XMLList(text_str.replace(r, function(m,m1,m2){return TAL.func(data, m1||m2);})) + (/\s$/.test(text_str) ? ' ' : '')
							 );
            }
        }
        if((tn=n.@tal::omit).length()) {
            if(TAL.func(data, tn)) {
				for each(var child in n.*){
					if((child..@tal::*).length()>0) TAL.TALE(child, data, tal);
				}
				n.parent().replace(n.childIndex(), n.*);
				return;
			} else{
				delete n.@tal::omit;
			}
        }
    for each (var child in n.*) {
        if((child..@tal::*).length()>0) TAL.TALE(child, data, tal);
    }
}
TAL.semisplit = function (s) {
    var r=[], v=s.split(';'), p;
    while((p=v.shift())!=null) {
        if(p.length==0) { r.push(r.pop()+';'+v.shift()); }
        else { r.push(p); }
    }
    return r;
}
TAL.RE_define = /^ *(global )?(local )? *([^ ]+) +(.+)$/;
TAL.RE_nameExpr = /^ *([^ ]+) +(.*)$/;
TAL.RE_trim = /^ *(.*?) *$/;
TAL.Repeat = function (i, c) { this.index = i; this.count = c; };
TAL.Repeat.prototype.number = function () { return parseInt(this.index,10) + 1; };
TAL.Repeat.prototype.even = function () { return ((this.index % 2)==0)?true:false; };
TAL.Repeat.prototype.odd = function () { return ((this.index % 2)==1)?true:false; };
TAL.Repeat.prototype.start = function () { return this.index==0; };
TAL.Repeat.prototype.end = function () { return this.index==(this.count-1); };
TAL.stringExpr = function(data, str) {
    var path_re=/\$\{([^}]+)\}|\$([^$ \/]+)/g, lastlast=0, parts=[], match, index;
	while((match=path_re.exec(str))!=null) {
		index = match.index;
		if(index==0 || str.charAt(index-1)!='$') {
			parts.push(str.slice(lastlast, index).replace('$$', '$'));
			parts.push(TAL.getPathExpr(data, match[1] || match[2])[1]);
		} else {
			parts.push(str.slice(lastlast, path_re.lastIndex).replace('$$', '$'));
		}
		lastlast = path_re.lastIndex;
	}
	parts.push(str.slice(lastlast).replace('$$','$'));
	return parts.join('');
};

/*
 * pathExpr - look up the value of a path variable in the current scope.
 *                returns a two-element array --> [bool, exprValue], where the
 *                first element is true if the path variable was defined, and the
 *                second element is the value itself of the variable
 */
TAL.getPathExpr = function(data, path) {
    var original_path = path;
    path = path.split("/");
    var value = data;
    var old_value;
    for(var j=0; j< path.length; j++) {
        old_value = value;
        if(!value) return [false, value];
        value = value[path[j]];
        switch(typeof value) {
        case "undefined":
            // support backwards compatible clown scoping
            var func = data['this'][path[j]];
            if(func && typeof func == "function"){
                return [true, func.call(data['this'], data)];
            }
            return [false, value];
            break;
        case "function":
            value = value.call(old_value);
            if(!value) return [false, value];
            break;
        }
    }
    return [true, value];
}
TAL.expr = function (data, value) {
    // look for conditional expressions
    var parts = value.split("|?");
    if(parts.length > 1){
        var conds = parts[1].split("|:");
        var result = TAL.expr(data, parts[0]);
        if(!result)
            return TAL.expr(data, conds[1]);
        else
            return TAL.expr(data, conds[0]);

    }
    // deal with pipes
    var terms = value.split("|");
    for(var i=0; i<terms.length; i++) {
        var term = terms[i];
        if(term.indexOf("string:")==0 || term.indexOf("javascript:")==0) {
            terms.splice(i, terms.slice(i).join('|'));
            break;
        }
    }
    for(var i=0; i < terms.length; i++) {
        var exists=false, nocall=false, string=false, not=false, javascript=false;
        var term = terms[i].replace(TAL.RE_trim, '$1');

        // look for prefixes
        while(true) {
            var term7 = term.slice(0, 7);
            switch(term7) {
            case 'exists:': exists=true; term=term.slice(7); continue;
            case 'nocall:': nocall=true; term=term.slice(7); continue;
            case 'string:': string=true; term=term.slice(7); continue;
            }
            if(term.slice(0, 4)=="not:") { not=true; term=term.slice(4); continue; }
            if(term.slice(0, 11)=="javascript:") { javascript=true; term=term.slice(11); continue; }
            break;
        }
        if(string) {
            return TAL.stringExpr(data, term);
        } else if(javascript) {
			return (new Function('path','data','return ' + term)).call(data['this'],function (path){ return TAL.getPathExpr(data, TAL.stringExpr(data, path))[1]; }, data);
        }
        var result = TAL.getPathExpr(data, TAL.stringExpr(data, term));
        if(!result[1] && i+1 < terms.length ){
            continue;
        }
        if(exists) return not ? !result[0] : result[0];
        return not ? !result[1] : result[1];
    }
}

TAL.TAL = function(n, data, globalData, tal) {
    var tn;
    if((tn=n.@tal::define).length()) {
            TAL.Scope.prototype = data;
            data = new TAL.Scope();
            for each (var t in TAL.semisplit(tn.toString())) {
                var glnx = t.match(TAL.RE_define);
                if(glnx[1]) {
                        globalData[glnx[3]] = TAL.expr(data, glnx[4]);
                } else {
                        data[glnx[3]] = TAL.expr(data, glnx[4]);
                }
            }
            delete n.@tal::define;
    }
    if((tn=n.@tal::condition).length()) {
            var r = TAL.expr(data, tn.toString());
            if(r) {
                delete n.@tal::['condition'];
            } else {
                delete n.parent().*[n.childIndex()];
                return;
            }
    }
    if((tn=n.@tal::['repeat-content']).length()) {
            delete n.@tal::['repeat-content'];
            var nn=new XML('<kill tal:repeat="'+tn+'" tal:omit-tag="string:1" xmlns:tal="'+tal.uri+'">'+n.toXMLString()+'</kill>');
            n.replace('*', nn);
    }
    if((tn=n.@tal::repeat).length()) {
        var nx = tn.toString().match(TAL.RE_nameExpr);
        var v = TAL.expr(data, nx[2]), p = n.parent();
        delete n.@tal::repeat;
        var c = v?v.length:0;

        var repeat_lambda = function(i){
            TAL.Scope.prototype = data;
            var repeat_data = new TAL.Scope();
            repeat_data['repeat'][nx[1]] = new TAL.Repeat(i, c);
            repeat_data[nx[1]] = v[i];
            p.insertChildBefore(n, n.copy());
            TAL.TAL(p.*[n.childIndex()-1], repeat_data, globalData, tal);
        };

        if(v instanceof MultiValue){
            // XXX workaround since multivalues aren't iteritable at the moment
            for(var i=0; i<c; i++)
                repeat_lambda(i);
        } else {
            for(var i in v)
                repeat_lambda(i);
        }
        delete p.*[n.childIndex()];
        return;
    }
    if((tn=n.@tal::content).length()) {
            n.replace('*', TAL.expr(data, tn.toString()));
            delete n.@tal::content;
    }
    if((tn=n.@tal::replace).length()) {
        n.parent().replace(n.childIndex(), TAL.expr(data, tn.toString()));
            return;
    }
    if((tn=n.@tal::attributes).length()) {
            for each (var t in TAL.semisplit(tn.toString())) {
                var nx = t.match(TAL.RE_nameExpr);
                var expr_result = TAL.expr(data, nx[2]);
                if(expr_result)
                    n.@[nx[1]] = expr_result;
            }
            delete n.@tal::attributes;
    }
    if((tn=n.@tal::text).length()) {
            var r=new RegExp('\\'+tn+'\\{([^}]+)\\}|\\'+tn+'(\\w+)','g');
            delete n.@tal::text;
            for each(var t in n.text())
                n.replace(t.childIndex(),
                          new XMLList(t.toXMLString().replace(r,
	            function(m,m1,m2){return TAL.expr(data, m1 || m2);})));
    }
    for each (var c in n.*) {
        TAL.TAL(c, data, globalData, tal);
    }
    if((tn=n.@tal::['omit-tag']).length()) {
            if(TAL.expr(data, tn.toString())) n.parent().replace(n.childIndex(), n.*.copy());
				else delete n.@tal::['omit-tag'];
    }
}