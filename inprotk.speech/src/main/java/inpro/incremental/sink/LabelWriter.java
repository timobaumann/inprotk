package inpro.incremental.sink;

import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.IU;
import inpro.util.TimeUtil;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * An IU left buffer that prints its contents to STDOUT.
 * The format used resembles wavesurfer label files 
 * @author timo
 */
public class LabelWriter extends FrameAwarePushBuffer {
	
    private boolean writeToFile = false;
    private boolean commitsOnly = false;
    private boolean writeToStdOut = true;
    private String fileName = "";
    
    ArrayList<IU> allIUs = new ArrayList<IU>();
    
	@Override
	public void hypChange(Collection<? extends IU> ius, List<? extends EditMessage<? extends IU>> edits) {
		/* Get the time first */
		String toOut = String.format(Locale.US, "Time: %.2f", 
				currentFrame * TimeUtil.FRAME_TO_SECOND_FACTOR);
		/* Then go through all the IUs, ignoring commits */
		boolean added = false;
		for (EditMessage<? extends IU> edit : edits) {
			IU iu = edit.getIU();
			switch (edit.getType()) {
			case ADD:
				if (!commitsOnly) {
					added = true;
					allIUs.add(iu);
				}
				break;
			case COMMIT:
				if (commitsOnly) {
					added = true;
					allIUs.add(iu);
				}
				break;
			case REVOKE:
//				when revoking, we can assume that we are working with a stack;
//				hence, the most recent thing added is the most recent thing revoked
				if (!allIUs.isEmpty()) {
					added = true;
					allIUs.remove(allIUs.size()-1);
				}
				break;
			default:
				break;
			
			}

		}
		
		toOut = String.format(Locale.US, "Time: %.2f", 
		currentFrame * TimeUtil.FRAME_TO_SECOND_FACTOR);
		for (IU iu : allIUs) {
			toOut += "\n" + iu.toLabelLine();
		}
		
		toOut += "\n\n";
		/* If there were only commits, or if there are not IUs, then print out as specified */
		if (edits.size() > 0 && added) { // && frameOutput != currentFrame) {
			if (writeToFile) {
				try {
					try (FileWriter writer = new FileWriter(fileName, true)) {
						writer.write(toOut);
						writer.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (writeToStdOut) {
				System.out.println(toOut);
			}
		}
	}
	
	public void setWriteToFile(boolean writeToFile) {
		this.writeToFile = writeToFile;
	}

	public void setFileName(String filename) {
		this.fileName = filename;
	}
	
}