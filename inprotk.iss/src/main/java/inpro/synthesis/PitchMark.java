package inpro.synthesis;

import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a PitchMark
 */
public class PitchMark {
	protected Double position; // as a percentage
	protected Double pitch; // presumably in Hz
	transient private String pitchMarkString; 

	protected PitchMark(double position, double pitch) {
		this.position = position;
		this.pitch = pitch;
	}
	
	/** create a pitchMark from an mbrola pitchmark-string */
	public PitchMark(String pitchMarkString) {
		this.pitchMarkString = pitchMarkString;
	}
	
	public void setPitch(float pitch) {
		this.pitch = (double) pitch;
	}

	public String toString() {
		if (position == null || pitch == null)
			parsePitchString();
		return "(" + ((int) (position * 100)) + "," + (pitch.intValue()) + ")"; 
	}
	
	/** returns the time of this pitch mark given the label's boundaries */
	public int getTime(int startTime, int duration) {
		return (int) (startTime + position * duration);
	}
	
	/** really be careful not to mess up the linear ordering of pitchmarks in a list of pms! */ 
	public void setRelativePosition(double timepos) {
		this.position = timepos;
	}

	@SuppressWarnings("resource")
	private void parsePitchString() {
		assert pitchMarkString.matches("\\(?(\\d+),(\\d+)\\)?") : pitchMarkString;
		Pattern format = Pattern.compile("\\(?(\\d+),(\\d+)\\)?");
		Matcher m = format.matcher(pitchMarkString);
		if (!m.matches()) { // we have to match, whether assertions are enabled or not
			assert false; 
		}
		assert m.groupCount() == 2;
		position = (new Scanner(m.group(1))).nextDouble() * 0.01;
		pitch = (new Scanner(m.group(2))).nextDouble();
	}
	
	public double getPosition() {
		if (position == null)
			parsePitchString();
		return this.position;
	}
	
	public int getPitch() {
		if (pitch == null)
			parsePitchString();
		return pitch.intValue();
	}
}