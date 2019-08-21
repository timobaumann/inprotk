package inpro.synthesis.hts;

import inpro.pitch.PitchUtils;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FullPFeatureFrame {

    private final double[] mcepParVec;
    private final double[] magParVec;
    private final double[] strParVec;
    public final boolean voiced;
    private double lf0Par;
    
    public FullPFeatureFrame(double[] mcep, double[] mag, double[] str, boolean voiced, double lf0Par) {
        mcepParVec = mcep;
        magParVec = mag;
        strParVec = str;
        this.voiced = voiced;
        this.lf0Par = lf0Par;
    }
    
    /** copy a different frame's data */
    public FullPFeatureFrame(FullPFeatureFrame frame) {
    	this(frame.getMcepParVec().clone(), 
    	     frame.getMagParVec().clone(), 
    	     frame.getStrParVec().clone(), 
    	     frame.isVoiced(), 
    	     frame.getlf0Par()
    	);
	}

	public double[] getMcepParVec() { return mcepParVec.clone(); }
    public double[] getMagParVec() { return magParVec; }
    public double[] getStrParVec() { return strParVec; }

    public int getMcepParSize() { return mcepParVec.length; }
    public int getStrParSize() { return strParVec.length; }

    public boolean isVoiced() { return voiced; }
    /** return log-f0 */
    public double getlf0Par() { return lf0Par; }
    /** return f0 in Hz */
    public double getF0Par() { return Math.exp(lf0Par); }
    
    public void shiftlf0Par(double pitchShiftInCent) {
    	if (voiced)
    		lf0Par += pitchShiftInCent * PitchUtils.BY_CENT_CONST;
    }
    
    public void setlf0Par(double lf0) {
    	lf0Par = lf0;
    }
    
    /** f0 in Hz */
    public void setf0Par(double f0) {
    	setlf0Par(Math.log(f0));
    }
    
    @Override
    public String toString() {
    	return "mcep: " + Arrays.toString(mcepParVec) + 
    		 ", mag: " + Arrays.toString(magParVec) + 
    		 ", str: " + Arrays.toString(strParVec) + 
        (voiced ? ", voiced with pitch " + Math.exp(lf0Par) + " Hz" : ", unvoiced");
    }

	static final Pattern fromStringPattern = Pattern.compile("mcep: \\[(.*)\\], mag: \\[(.*)\\], str: \\[(.*)\\], (.*)voiced(.*)");
	
	static double[] stringToDoubleList(String s) {
		String[] sarray = s.split(", ");
		double[] darray = new double[sarray.length];
		for (int i = 0; i < sarray.length; i++) {
			darray[i] = Double.parseDouble(sarray[i]);
		}
		return darray;
	}
	
	// TODO from UCDetector: Method "FullPFeatureFrame.fromString(String)" has 0 references
	// this functionality should be tested and be referenced by that test
	public static FullPFeatureFrame fromString(String s) { // NO_UCD (unused code)
		Matcher m = fromStringPattern.matcher(s);
		boolean b = m.matches();
		assert b && m.groupCount() == 5 : s + " does not match " + fromStringPattern.toString();
		double[] mcep = stringToDoubleList(m.group(1));
		double[] mag = stringToDoubleList(m.group(2));
		double[] str = stringToDoubleList(m.group(3));
		boolean voiced = m.group(4).equals("");
		double lf0Par = 0.0;
		if (voiced) {
			String lf0String = m.group(5).replaceFirst(" with pitch ", "").replaceAll(" Hz", "");
			lf0Par = Double.parseDouble(lf0String);
		}
		return new FullPFeatureFrame(mcep, mag, str, voiced, lf0Par);
	}

}
