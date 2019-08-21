package inpro.util;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class PathUtil {

	/**
	 * getURLForPath try to use the given path to either make
	 * directly an URL or use it as path to a file and take
	 * the URL from there
	 * @param path URL or normal path
	 * @return URL if it is able to build one directly or out of path
	 */
	public static URL anyToURL(String path) throws MalformedURLException {
		URL result;
		//first try to read the given string as an URL
		try {
			result = new URL(path);
			return result;
		} catch(MalformedURLException murle) {
			/*if it wasn't a string try to read it as file path
			 *the catching part should be useless since there will be a file not found exception
			 */
			result = new File(path).toURI().toURL();
			return result;
		}
	}
	
	public static URL anyToURL(File file) throws MalformedURLException {
		return file.toURI().toURL();
	}
}
