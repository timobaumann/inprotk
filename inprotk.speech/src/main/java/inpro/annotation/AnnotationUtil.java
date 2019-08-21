package inpro.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** static helper function used to parse Praat TextGrid files */
public class AnnotationUtil {

	static List<String> interpret(List<String> lines, List<Pattern> patterns) throws IOException {
		assert lines.size() >= patterns.size();
		List<String> params = new ArrayList<String>(patterns.size());
		Iterator<String> lineIt = lines.iterator();
		for (Pattern pattern : patterns) {
			String line = lineIt.next();
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches()) {
				params.add(matcher.groupCount() >= 1 ? matcher.group(1) : "");
			} else {
				throw new IOException("malformatted TextGrid: " + line);
			}
		}
		return params;
	}

}
