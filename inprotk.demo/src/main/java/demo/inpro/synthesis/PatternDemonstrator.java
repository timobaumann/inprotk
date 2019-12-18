package demo.inpro.synthesis;

import inpro.audio.DispatchStream;
import inpro.incremental.sink.CurrentHypothesisViewer;
import inpro.incremental.unit.*;
import inpro.incremental.unit.IU.IUUpdateListener;
import inpro.incremental.unit.IU.Progress;
import inpro.synthesis.MaryAdapter;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * implementing classes should<ol>
 * <li>call super() in their constructor
 * <li>place the JEditorPane {@link PatternDemonstrator#generatedText} somewhere
 * <li>hook up {@link PatternDemonstrator#goAction} which starts synthesis
 * </ol>
 * @author timo
 *
 */
public abstract class PatternDemonstrator extends JPanel {
	
	/** a text field that displays the current value of {@link PatternDemonstrator#installment} */
	final JEditorPane generatedText;
	/** the IU holding the incrementally synthesized installment */
	SysInstallmentIU installment;
	
	CurrentHypothesisViewer viewer;
	
	DispatchStream dispatcher;
	
	IUUpdateListener iuUpdateRepainter = new IUUpdateListener() {
		Progress previousProgress;
		@Override
		public void update(IU updatedIU) {
			Progress newProgress = updatedIU.getProgress();
			if (newProgress != previousProgress) {
				previousProgress = newProgress;
				synchronized(generatedText) {
					viewer.hypChange(installment.getWords(), new ArrayList<EditMessage<IU>>() );
					 //TODO: Even if CHV does not use the edit messages they should be supported here
				}
			}
		}
	};
	
	/** used to update installment actions before a new installment is played */
	List<InstallmentAction> installmentActions = new ArrayList<InstallmentAction>();

	/** the action to execute in order to start synthesis */
	Action goAction = new AbstractAction("", new ImageIcon(PatternDemonstrator.class.getResource("media-playback-start.png"))) {
		@Override
		public void actionPerformed(ActionEvent e) {
			goAction.setEnabled(false);
			System.err.println("attempting to start playback onto dispatcher");
	        dispatcher.playStream(installment.getAudio(), false);
		}
	};

	/** action that triggers a new installment to be created and hooked up with the installment actions*/
	class StartAction extends AbstractAction {
		public StartAction(String name) { super(name); }
		public StartAction(String name, Icon icon) { super(name, icon); }
		@Override
		public void actionPerformed(ActionEvent ae) {
			//setEnabled(false);
			greatNewUtterance(ae.getActionCommand());
			for (IU word : installment.groundedIn()) {
				word.updateOnGrinUpdates();
				word.addUpdateListener(iuUpdateRepainter);
			}
			for (InstallmentAction ia : installmentActions) {
				ia.setEnabled(true);
				ia.updateParentNodes((TreeStructuredInstallmentIU) installment);
			}
			goAction.setEnabled(true);
		}
	}
	
	class InstallmentAction extends AbstractAction {
		final List<WordIU> parentNodes = new ArrayList<WordIU>();
		int posInUtt;
		InstallmentAction(String name, int pos) { 
			super(name);
			posInUtt = pos;
		}
		void updateParentNodes(TreeStructuredInstallmentIU installment) {
			parentNodes.clear();
			parentNodes.addAll(installment.getWordsAtPos(posInUtt));
		}
		@Override
		public void actionPerformed(ActionEvent ae) {
			if (!installment.isCompleted()) {
				for (WordIU parent : parentNodes) {
					if (!parent.isCompleted()) {
						parent.setAsTopNextSameLevelLink(ae.getActionCommand());
					} else {
						System.err.println("trying to fix what I can");
						WordIU currentWord = (WordIU) installment.getOngoingGroundedIU();
						System.err.println("currently ongoing: " + currentWord);
						WordIU wordToDeliver = (WordIU) parent.getAmongNextSameLevelLinks(ae.getActionCommand());
						System.err.println("wordToDeliver: " + wordToDeliver);
						if (wordToDeliver != currentWord) { // weird (but necessary) test
							if (currentWord == null) return;
							currentWord.addNextSameLevelLink(wordToDeliver);
							SysSegmentIU firstVarSegment = (SysSegmentIU) wordToDeliver.getSegments().get(0);
							SegmentIU lastCommonSegment = currentWord.getSegments().get(currentWord.getSegments().size() - 1);
							firstVarSegment.shiftBy(lastCommonSegment.endTime() - firstVarSegment.startTime(), true);
							lastCommonSegment.addNextSameLevelLink(firstVarSegment);
							lastCommonSegment.setAsTopNextSameLevelLink(firstVarSegment.toPayLoad());
						}
					}
				}
				
				viewer.hypChange(installment.getWords(), new ArrayList<EditMessage<IU>>());
				//TODO: Even if CHV does not use the edit messages they should be supported here
			} else 
				System.err.println("too late");
		}
	}

	protected PatternDemonstrator() {
		MaryAdapter.getInstance();
		try {
			AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000.f, 16, 1, (16/8)*1, 16000.f, false);
			DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
			if (!AudioSystem.isLineSupported(info)) {
				throw new RuntimeException("Line matching " + info + " not supported.");
			}
			final SourceDataLine line;
			line = (SourceDataLine) AudioSystem	.getLine(info);
			line.open(format, 1280);
			System.err.println("speaker actually has buffer size " + line.getBufferSize());

			dispatcher = new DispatchStream();

			Runnable streamDrainer = () -> {
                byte[] b = new byte[320]; // that will fit 10 ms
                while (true) {
                    int bytesRead = 0;
                    try {
                        bytesRead = dispatcher.read(b, 0, b.length);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    if (bytesRead > 0)
                        // no need to sleep, because the call to the microphone will already slow us down
                        line.write(b, 0, bytesRead);
                    else {// if there is no data, then we wait a little for data to become available (instead of looping like crazy)
                        if (bytesRead <= 0 && dispatcher.inShutdown())
                            return;
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

			new Thread(streamDrainer, "streamToSpeakers").start();
			line.start();
		} catch (LineUnavailableException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		viewer = new CurrentHypothesisViewer();
		generatedText  = viewer.getTextField();
		generatedText.setPreferredSize(new JTextField(30).getPreferredSize());
		generatedText.setEditable(false);
	}
	
	/** this operation is called by a StartAction and should (re-)create {@link PatternDemonstrator#installment} */
	public abstract void greatNewUtterance(String command);
	
	public String applicationName() {
		return "Test Application";
	}
	
	/** used to create GUI on the Swing event thread */
	public static void createAndShowGUI(PatternDemonstrator panel) {
		/** startup mary */
		MaryAdapter.getInstance();
		
		/** disable global variance optimization */
		//((HMMVoice) Voice.getVoice(MaryAdapter4internal.DEFAULT_VOICE)).getHMMData().setUseGV(false);
		JFrame frame = new JFrame(panel.applicationName());
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// add our object
		frame.setContentPane(panel);
		//Display the window.
        frame.pack();
		frame.setVisible(true);
	}
	

}