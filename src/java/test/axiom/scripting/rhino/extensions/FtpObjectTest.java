package test.axiom.scripting.rhino.extensions;

import axiom.scripting.rhino.extensions.FtpObject;
import junit.framework.TestCase;
public class FtpObjectTest extends TestCase{

  	public void testFtpObject()throws Exception{
  		FtpObject ftp = new FtpObject("americascommunitybankers.com");
  	    ftp.login("nasdaq","nasdaq21");
  	    ftp.binary();
  	    assertTrue(ftp.getFile("crsp_daily_index_acbq.20060920","c:\\crsp_daily_index_acbq.20060920"));
  	    ftp.logout();
	}

}
