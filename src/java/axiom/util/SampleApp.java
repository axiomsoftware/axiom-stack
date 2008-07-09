package axiom.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SampleApp {
	//default property files
	private final String APP_FILE = "app.properties";
	private final String APP_PROPS =
		"debug = true\n" +
		"tal = true\n" +
		"trigger = true\n" +
		"requestTimeout=360\n" +
		"File.repository=/axiom-files/files\n" +
		"Image.repository=/axiom-files/images\n" +
		"automaticResourceUpdate = true";      
	
	private final String PROTOTYPE_FILE = "prototype.properties";
	private final String PROTOTYPE_PROPS = 
		"_children\n" + 
		"_children.type = Collection(AxiomObject)\n" +
		"_children.accessname = id\n" +
		"\n" +
		"id\n" +
		"\n" +
		"meta_keys\n" +
		"meta_keys.type = String\n" +
		"meta_keys.widget = textarea\n" +
		"meta_keys.widget.label = Meta Keywords\n" +
		"\n" +
		"meta_desc\n" +
		"meta_desc.type = String\n" +
		"meta_desc.widget = textarea\n" +
		"meta_desc.widget.label = Meta Description\n" +
		"\n" +
		"title\n" +
		"title.type = String\n" +
		"title.widget = textbox\n" +
		"title.widget.label = Title";
	
	private final String SECURITY_FILE = "security.properties";
	private final String SECURITY_PROPS = "main=@Anyone\n";
	
	private final String SAMPLE_FILE = "sample.tal";
	private final String SAMPLE_CONTENT =
		"<div xmlns:tal=\"http://xml.zope.org/namespaces/tal\" style=\"clear:left;\">\n" +
		"  <h1>Sample Axiom Content</h1>\n" +
		"  <table>\n" +
		"    <tr>\n" +
		"      <td>This is only an example!</td>\n" +
		"    </tr>\n" +
		"  </table>\n" +
		"</div>";
	
	public void setupSampleApp(File appDir) throws IOException{
		// create default examples w/prop files none exist
		File f = new File(appDir + "/" + this.APP_FILE);
		if(!f.exists()){
			FileWriter out = new FileWriter(f);
			out.write(this.APP_PROPS);
			out.close();
			File rDir = new File(appDir,"/Root");
			if (!rDir.exists()) {
				rDir.mkdirs();
				//write sample properties
				f = new File(rDir, this.PROTOTYPE_FILE);
				out = new FileWriter(f);
				out.write(this.PROTOTYPE_PROPS);
				out.close();
				f = new File(rDir, this.SECURITY_FILE);
				out = new FileWriter(f);
				out.write(this.SECURITY_PROPS);
				out.close();
				f = new File(rDir, this.SAMPLE_FILE);
				out = new FileWriter(f);
				out.write(this.SAMPLE_CONTENT);
				out.close();   
			}
			File gDir = new File(appDir,"/Global");
			if (!gDir.exists()){
				gDir.mkdirs();
				f = new File(gDir, this.SECURITY_FILE);
				out = new FileWriter(f);
				out.write(this.SECURITY_PROPS);
				out.close();
			}
			File hDir = new File(appDir,"/AxiomObject");
			if (!hDir.exists()) {
				hDir.mkdirs();
			}
		}
	}
}
