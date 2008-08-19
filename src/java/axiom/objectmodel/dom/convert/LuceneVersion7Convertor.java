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
package axiom.objectmodel.dom.convert;

import java.sql.Connection;
import java.text.DecimalFormat;
import java.util.Enumeration;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;

import axiom.framework.core.Application;
import axiom.framework.core.Prototype;
import axiom.objectmodel.IProperty;
import axiom.objectmodel.dom.LuceneManager;
import axiom.util.ResourceProperties;

public class LuceneVersion7Convertor extends LuceneConvertor {

    DecimalFormat datedf = new DecimalFormat("000000");
    DecimalFormat timedf = new DecimalFormat("00000000000");
    DecimalFormat timestampdf = new DecimalFormat("00000000000000");
    DecimalFormat intdf = new DecimalFormat("0000000000");
    DecimalFormat floatdf = new DecimalFormat("0000000000.0000");
    DecimalFormat smallfldf = new DecimalFormat("0000.0000");
    DecimalFormat smallintdf = new DecimalFormat("0000");
    
    public LuceneVersion7Convertor(Application app) {
        super(app);
    }
    
    protected Document convertDocument(Document doc) throws Exception {
        Document ndoc = new Document();
        Enumeration e = doc.fields();
        
        final String prototype = doc.get("_prototype");
        final Prototype proto = app.getPrototypeByName(prototype);
                
        while (e.hasMoreElements()) {
            Field f = (Field) e.nextElement();
            Field.Store currstore = Field.Store.YES;
            if (!f.isStored()) {
                currstore = Field.Store.NO;
            } else if (f.isCompressed()) {
                currstore = Field.Store.COMPRESS;
            }
            Field.Index curridx = Field.Index.UN_TOKENIZED;
            if (!f.isIndexed()) {
                curridx = Field.Index.NO;
            } else if (f.isTokenized()) {
                curridx = Field.Index.TOKENIZED;
            }
                    
            String name = f.name();
            String value = f.stringValue();
            final int type = this.getTypeForProperty(proto, name);
            if (type == IProperty.DATE) {
                value = formatDate(value);
            } else if (type == IProperty.TIME) {
                value = formatTime(value);
            } else if (type == IProperty.TIMESTAMP) {
                value = formatTimestamp(value);
            } else if (type == IProperty.INTEGER) {
                value = formatInt(value);
            } else if (type == IProperty.FLOAT) {
                value = formatFloat(value);
            } else if (type == IProperty.SMALLFLOAT) {
                value = formatSmallFloat(value);
            } else if (type == IProperty.SMALLINT) {
                value = formatSmallInt(value);
            }
            ndoc.add(new Field(name, value, currstore, curridx));                    
        }
        
        return ndoc;
    }

    protected void updateSQL(Connection conn) throws Exception {
        return;
    }
    
    private String formatDate(String value) {
        String result = null;
        try {
            result = datedf.format(Integer.parseInt(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private String formatTime(String value) {
        String result = null;
        try {
            result = timedf.format(Integer.parseInt(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private String formatTimestamp(String value) {
        String result = null;
        try {
            result = timestampdf.format(Integer.parseInt(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private String formatInt(String value) {
        String result = null;
        try {
            result = intdf.format(Integer.parseInt(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private String formatFloat(String value) {
        String result = null;
        try {
            result = floatdf.format(Float.parseFloat(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private String formatSmallFloat(String value) {
        String result = null;
        try {
            result = smallfldf.format(Float.parseFloat(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private String formatSmallInt(String value) {
        String result = null;
        try {
            result = smallintdf.format(Integer.parseInt(value));
        } catch (Exception ex) {
            result = value;
        }
        return result;
    }
    
    private int getTypeForProperty(Prototype prototype, String property) {
        if (prototype == null) {
            return IProperty.STRING;
        }
        ResourceProperties props = prototype.getAllProps();
        String strtype = (String) props.get(property + ".type");
        return LuceneManager.stringToType(strtype);
    }

}