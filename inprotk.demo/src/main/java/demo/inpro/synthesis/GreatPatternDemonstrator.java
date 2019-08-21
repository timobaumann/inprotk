package demo.inpro.synthesis;

import inpro.incremental.unit.TreeStructuredInstallmentIU;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Arrays;


/**
 * this prototype will allow the user to generate utterances like
 * "&lt;Action> bitte das &lt;Color> &lt;Piece>" (for now, later maybe as below)
 * <p>
 * first, something like:
 * <pre>
 *           ╱  nimm  ╲             ╱ rote  ╲  ╱   Kreuz    ╲
 * (Jetzt?) ❬         ❭ bitte das ❬  grüne  ❭❬      T      ❭ </s>
 *           ╲ lösche ╱             ╲ blaue ╱  ╲ lange Teil ╱
 *            ╲ äh ...               ╲ äh ...   ╲ äh ...
 * </pre>
 * <p>
 * "Nimm bitte das CCCC Kreuz und lege es nach LLLL."
 * with CCCC being a color (such as "rote", "grüne", "blaue"
 * and LLLL being a location (such as "oben links", or "unten rechts")
 * <p>
 * 
 * @author timo
 */
public class GreatPatternDemonstrator extends PatternDemonstrator {
	
	public static final int COLOR_POSITION = 2;
	public static final int PIECE_POSITION = 3;
	
	/** a start action that also executes goAction */
	class ImmediateStartAction extends StartAction {
		public ImmediateStartAction(String name, Icon icon) { super(name, icon); }		
		@Override
		public void actionPerformed(ActionEvent ae) {
			super.actionPerformed(ae);
			goAction.actionPerformed(ae);
		}
	}
	
	ButtonGroup actionGroup = new ButtonGroup();
	JButton actionButton(AbstractAction aa) {
		JButton b = new JButton(aa);
		actionGroup.add(b);
		return b;
	}

	ButtonGroup colorGroup = new ButtonGroup();
	JToggleButton colorButton(Color c, String name) {
		InstallmentAction ia = new InstallmentAction(name, COLOR_POSITION);
		installmentActions.add(ia);
		JToggleButton b = new JToggleButton(ia);
		colorGroup.add(b);
		b.setBackground(c);
		return b;
	}
	
	ButtonGroup pieceGroup = new ButtonGroup();
	JToggleButton pieceButton(char type, String name) {
		InstallmentAction ia = new InstallmentAction(name, PIECE_POSITION);
		installmentActions.add(ia);
		JToggleButton b = new JToggleButton(ia);
		//b.setIcon(new PentoIcon(7, Color.GRAY, type));
		pieceGroup.add(b);
		return b;
	}
		
	GreatPatternDemonstrator() {
		super();
		setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridwidth = 4;
		gbc.gridheight = 1;
		gbc.gridx = 1;
		gbc.gridy = 1;
		add(generatedText, gbc);
		gbc.gridwidth = 1;
		gbc.gridy++;
		gbc.gridx = 2;
		add(actionButton(new ImmediateStartAction("Nimm", new ImageIcon(GreatPatternDemonstrator.class.getResource("dragging.png")))), gbc);
		gbc.gridx++;
		add(actionButton(new ImmediateStartAction("Lösche", new ImageIcon(GreatPatternDemonstrator.class.getResource("cross.png")))), gbc);
		gbc.gridy++;
		gbc.gridx = 1;
		/* add(new JButton(new AbstractAction("reset") {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				colorGroup.clearSelection();
			}
		}), gbc); */
		gbc.gridx++;
		add(colorButton(Color.RED, "rote"), gbc);
		gbc.gridx++;
		add(colorButton(Color.BLUE, "blaue"), gbc);
		gbc.gridx++;
		add(colorButton(Color.GREEN, "grüne"), gbc);
		gbc.gridy++;
		gbc.gridx = 1;
		/* add(new JButton(new AbstractAction("reset") {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				pieceGroup.clearSelection();
			}
		}), gbc); */
		gbc.gridx++;
		add(pieceButton('X', "Kreuz"), gbc);
		gbc.gridx++;
		add(pieceButton('T', "T"), gbc);
		gbc.gridx++;
		add(pieceButton('I', "gerade Teil"), gbc);
	}


	
   /**
	 *  Nimm  ╲             ╱ rote  ╲  ╱    Kreuz    ╲
	 *         ❭ bitte das ❬  grüne ❭❬       T      ❭ </s>
	 * Lösche ╱             ╲ blaue ╱  ╲ gerade Teil ╱
	 *                       ╲ äh ...   ╲ äh ...
	 */
	@Override
	public void greatNewUtterance(String command) {
		installment = new TreeStructuredInstallmentIU(Arrays.asList(
				command + " bitte das, äh?", 
				command + " bitte das rote, äh?", 
				command + " bitte das rote Kreuz",
				command + " bitte das rote T",
				command + " bitte das rote gerade Teil",
				command + " bitte das grüne, äh?",
				command + " bitte das grüne Kreuz",
				command + " bitte das grüne T",
				command + " bitte das grüne gerade Teil",
				command + " bitte das blaue, äh?",
				command + " bitte das blaue Kreuz",
				command + " bitte das blaue T",
				command + " bitte das blaue gerade Teil"
				));
		generatedText.setText(command + " bitte das ‹color› ‹piece›");
	}

	/**
	 * main method for testing: creates a PentoCanvas that shows all tiles and the grid.
	 * @param args arguments are ignored
	 */
	public static void main(String args[]) {
	//	PropertyConfigurator.configure("log4j.properties");
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				createAndShowGUI(new GreatPatternDemonstrator());
			}
		});
	}

}
