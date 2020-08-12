package demo.inpro.synthesis;

import inpro.incremental.unit.IU;
import inpro.incremental.unit.SysSegmentIU;
import inpro.incremental.unit.TreeStructuredInstallmentIU;
import inpro.synthesis.hts.LoudnessPostProcessor;
import inpro.synthesis.hts.VocodingAudioStream;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;


public class ProsodyDemonstrator extends PatternDemonstrator {
	
	public ProsodyDemonstrator() {
		generatedText.setPreferredSize(new JTextField(42).getPreferredSize());
		//generatedText.setText("Press the play button to synthesize this utterance."); 
		generatedText.setEditable(true);
		String synText = System.getProperty("inpro.tts.demo.longUtt", "Nimm bitte das Kreuz ganz oben links in der Ecke, lege es in den Fuss des Elefanten bevor Du ihn auf den Kopf drehst.");
		generatedText.setText(synText);
		final BoundedRangeModel tempoRange = new DefaultBoundedRangeModel(0, 0, -100, 100);
		final BoundedRangeModel pitchRange = new DefaultBoundedRangeModel(0, 0, -1200, 1200);
		final BoundedRangeModel strengthRange = new DefaultBoundedRangeModel(0, 0, -100, 100);
		final BoundedRangeModel loudnessRange = new DefaultBoundedRangeModel(10, 0, 1, 100); // to be interpreted by *.1
		final BoundedRangeModel stressingRange = new DefaultBoundedRangeModel(0, 0, 0, 100);
		tempoRange.addChangeListener(tempoChangeListener);
		pitchRange.addChangeListener(pitchChangeListener);
		strengthRange.addChangeListener(strengthChangeListener);
		loudnessRange.addChangeListener(loudnessChangeListener);
		stressingRange.addChangeListener(e -> {
            double value = getSourceValue(e); // should be normalized between -100 and +100
            strengthPostProcessor.setLoudness((int) value);
            tempoChangeListener.performChange(value / 100f);
            pitchChangeListener.performChange(value);
            //loudness:
            VocodingAudioStream.gain = 0.3* Math.exp((value * .01) * Math.log(2));
        });
		this.add(generatedText);
		this.add(new JButton(new AbstractAction("", new ImageIcon(ProsodyDemonstrator.class.getResource("media-playback-start.png"))) {
			@Override
			public void actionPerformed(ActionEvent e) {
				System.err.println(generatedText.getText());
				String html = generatedText.getText();
				String txt = html.replaceAll("<.*?>", "").replaceAll("^[\\n\\w]+", "").replaceAll("[\\n\\w]+$", "").trim();
				System.err.println(txt);
				greatNewUtterance(txt);
		        dispatcher.playStream(installment.getAudio(), true);
		        for (SysSegmentIU seg : getSegments()) {
		        	seg.setVocodingFramePostProcessor(strengthPostProcessor);
		        }
//		        tempoRange.setValue(0);
		        tempoChangeListener.stateChanged(new ChangeEvent(tempoRange));
//		        pitchRange.setValue(0);
		        pitchChangeListener.stateChanged(new ChangeEvent(pitchRange));
//		        strengthRange.setValue(0);
		        strengthChangeListener.stateChanged(new ChangeEvent(strengthRange));
//		        loudnessRange.setValue(10);
		        loudnessChangeListener.stateChanged(new ChangeEvent(loudnessRange));
			}
		}));
		this.add(createSlider("tempo:", tempoRange, "0.5", "0.7", "1.0", "1.4", "2.0"));
		this.add(createSlider("pitch:", pitchRange, "-12", "-6", "0", "+6", "+12"));
		this.add(createSlider("voice:", strengthRange, "", "softer", "", "stronger", ""));
		this.add(createSlider("loudness:", loudnessRange, ".1", "", "1.0", "", "10"));
		this.add(createSlider("distanceStressing:", stressingRange, "0", "", "50", "", "100"));
	}
	
	/** create a label and a slider for a given rangeModel with equidistant labels */ 
	private static JComponent createSlider(String label, BoundedRangeModel rangeModel, String... labels) {
		JPanel panel = new JPanel();
		panel.add(new JLabel(label));
		JSlider slider = new JSlider(rangeModel);
		int min = rangeModel.getMinimum();
		int max = rangeModel.getMaximum();
		slider.setPaintLabels(true);
		slider.setPaintTicks(true);
		int range = max - min;
		slider.setMajorTickSpacing(range / 2);
		slider.setMinorTickSpacing(range / 4);
		slider.setLabelTable(createLabelTable(min, max, labels));
		panel.add(slider);
		return panel;
	}
	
	/** create a hashtable of position/label pairs, equidistantly spaced between min and max */
	private static Hashtable<Integer, JComponent> createLabelTable(int min, int max, String... labels) {
		Hashtable<Integer, JComponent> labelHash = new Hashtable<Integer, JComponent>();
		int increment = (max - min) / (labels.length - 1);
		for (String label : labels) {
			labelHash.put(min, new JLabel(label));
			min += increment;
		}
		return labelHash;
	}
	
	private static int getSourceValue(ChangeEvent e) {
		BoundedRangeModel brm = (BoundedRangeModel) e.getSource();
		return brm.getValue();
	}
	
	interface MyChangeListener extends ChangeListener {
		public void performChange(double value);
	}
	
	/**
	 * sets the pitchShiftInCent value of every segment in the utterance to the value indicated by the slider-model
	 */
	final MyChangeListener pitchChangeListener = new MyChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			double offset = getSourceValue(e);
			performChange(offset);
		}
		// scale for value is -1200 to +1200
		public void performChange(double offset) {
// values of -1000 should reduce variance from 86 to 0, of +1000 excursions should be twice as large
//			offset /= 1200; // normalize to [-1;+1]
			for (SysSegmentIU seg : getSegments()) {
//				if (seg.getFromNetwork("up").getUserData("accent") != null) {
//					System.err.println("stressed syllable:" + seg.getFromNetwork("up").getUserData("accent"));
//				} else {
//					System.err.println("unstressed syllable!");
//				}
				seg.pitchShiftInCent = offset;
//				seg.pitchExcitationFactor = offset;
			}
		}
	};
	
	/**
	 * stretches every segment in the utterance with the value indicated by the slider-model
	 */
	final MyChangeListener tempoChangeListener = new MyChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			double factor = getSourceValue(e) / 100f; // normalize to [-1;+1]
			performChange(factor);
		}
		public void performChange(double factor) {
			factor = Math.exp(factor * Math.log(2)); // convert to [.5;2]
			for (SysSegmentIU seg : getSegments()) {
				if (seg.isVowel())
					if (seg.getFromNetwork("up").getUserData("stress").toString().equals("1"))
						seg.stretchFromOriginal(factor + .1);
					else 
						seg.stretchFromOriginal(factor);
			}
		}
	};
	
	final LoudnessPostProcessor strengthPostProcessor = new LoudnessPostProcessor();
	final ChangeListener strengthChangeListener = e -> strengthPostProcessor.setLoudness(getSourceValue(e));
	
	final ChangeListener loudnessChangeListener = e -> VocodingAudioStream.gain = 0.3 * getSourceValue(e) * .1;

	/** return the segments in the ongoing utterance (if any) */ 
	private List<SysSegmentIU> getSegments() {
		if (installment != null)
			return installment.getSegments();
		else
			return Collections.emptyList();
	}
	
	@Override
	public void greatNewUtterance(String command) {
		installment = new TreeStructuredInstallmentIU(Collections.singletonList(command));
		for (IU word : installment.groundedIn()) {
			word.updateOnGrinUpdates();
			word.addUpdateListener(iuUpdateRepainter);
		}
		System.err.println("created a new installment: " + command);
	}
	
	@Override
	public String applicationName() {
		return "Prosody Demonstrator";
	}
	
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> createAndShowGUI(new ProsodyDemonstrator()));
	}

}
