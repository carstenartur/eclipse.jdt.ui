package trycatch_out;

import java.io.File;
import java.net.MalformedURLException;

public class TestWrappedLocal1 {

	public void foo() {
		File file= null;
		
		/*]*/int i= 10;
		try { 
			file.toURL();
		} catch(MalformedURLException e) {
		}/*[*/
		
		i= 20;
	}

}
