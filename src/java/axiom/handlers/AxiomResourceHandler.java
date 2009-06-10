package axiom.handlers;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.handler.ResourceHandler;
import org.mortbay.resource.Resource;

public class AxiomResourceHandler extends ResourceHandler {
	public void doResponseHeaders(HttpServletResponse response, Resource resource, String mimeType) {
		super.doResponseHeaders(response, resource, mimeType);
		Date d = new Date();
		d.setYear(d.getYear()+1);
		response.addHeader("Expires", new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss").format(d) + " GMT");
		
		if (mimeType.contains("javascript") || mimeType.contains("css")) {
			response.addHeader("Vary", "Accept-Encoding");
			response.addHeader("Cache-control", "private");
		} else {
			response.addHeader("Cache-control", "public");
		}
	}
}
