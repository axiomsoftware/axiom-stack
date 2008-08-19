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
package axiom.objectmodel.dom;

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import axiom.objectmodel.DatabaseException;


public class MetadataRetriever extends HashMap {
	private static final long serialVersionUID = 2225937548695215448L;
	private Properties props = new Properties();
	private final String[] DEFAULT_TYPE_ARRAY = {"string","int","float","date","text"};
	private final String[] DEFAULT_ATTR_ARRAY = {"format", "store", "index"};
	private final String[][] DEFAULT_DATA_ARRAY = {
			{"","yes","untokenized"},
			{"0000000000","yes","untokenized"},
			{"0000000000.0000","yes","untokenized"},
			{"yyyy.MM.dd HH:mm:ss","yes","untokenized"},
			{"","yes","untokenized"}
		};
	private final String METADATA_PROP_FILE = "types.properties";
	
	public MetadataRetriever(File appHome) throws DatabaseException
	{
		String metaFile = appHome.getAbsolutePath();
		if (!metaFile.endsWith(File.separator)){
			metaFile += File.separator;
		}
		metaFile += METADATA_PROP_FILE;

		try {			
			if(!new File(metaFile).exists()){
				this.createPropFile(metaFile);
			}else{
				props.load(new FileInputStream(metaFile));
				this.populateMap();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new DatabaseException("ERROR initializing the metadata from the metadata xml file");
		}
	}

	private void createPropFile(String metaFile) throws Exception{
		StringBuffer sb = new StringBuffer();
		for(int i=0;i<this.DEFAULT_TYPE_ARRAY.length;i++){
			String key=this.DEFAULT_TYPE_ARRAY[i];
			sb.append(key+"\n");
			HashMap attrMap = new HashMap();
			for(int j=0;j<this.DEFAULT_ATTR_ARRAY.length;j++){	
				sb.append(key+"."+this.DEFAULT_ATTR_ARRAY[j]+"="+this.DEFAULT_DATA_ARRAY[i][j]+"\n");
				attrMap.put(this.DEFAULT_ATTR_ARRAY[j],this.DEFAULT_DATA_ARRAY[i][j]);
			}
			this.put(key, attrMap);
		}
		File f = new File(metaFile);
		FileWriter out = new FileWriter(f);
		out.write(sb.toString());
		out.close();
	}

	private void populateMap() {
		int length = this.DEFAULT_ATTR_ARRAY.length;
		String key = "";
		for(Iterator it=props.keySet().iterator();it.hasNext();){
			key=(String)it.next();
			if(!key.contains(".")){
				HashMap attrMap = new HashMap();
				for (int i = 0; i < length; i++) {
					attrMap.put(this.DEFAULT_ATTR_ARRAY[i], props.get(key+"."+this.DEFAULT_ATTR_ARRAY[i]));
				}
				this.put(key, attrMap);
			}
		}
	}
}