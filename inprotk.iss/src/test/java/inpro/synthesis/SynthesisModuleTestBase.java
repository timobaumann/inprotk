package inpro.synthesis;

import java.util.Collection;
import java.util.List;

import inpro.incremental.processor.AdaptableSynthesisModule;
import org.apache.log4j.Logger;

import inpro.audio.DispatchStream;
import inpro.incremental.IUModule;
import inpro.incremental.unit.ChunkIU;
import inpro.incremental.unit.EditMessage;
import inpro.incremental.unit.EditType;
import inpro.incremental.unit.IU;
import inpro.incremental.unit.IU.IUUpdateListener;

public abstract class SynthesisModuleTestBase {

	protected static String[][] testList = {
		{ "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun" }, 
		{ "Nimm bitte das Kreuz und lege es in den Kopf des Elefanten." },
		{ "Nimm bitte das Kreuz", "und lege es in den Kopf des Elefanten." },
		{ "Nimm das Kreuz,", "das rote Kreuz,", "und lege es in den Kopf des Elefanten."}, 
	};
	
	protected DispatchStream dispatcher;
	protected TestIUModule myIUModule;
	/** not used in the basic SynthesisModuleUnitTest but in more elaborate tests: */
	AdaptableSynthesisModule asm;
		
	protected static class TestIUModule extends IUModule {
		protected void leftBufferUpdate(Collection<? extends IU> ius, 
				List<? extends EditMessage<? extends IU>> edits) { } // do nothing, this is only a source of IUs
		
		void addIUAndUpdate(IU iu) {
			rightBuffer.addToBuffer(iu);
			notifyListeners();
		}
		
		void revokeIUAndUpdate(IU iu) {
			rightBuffer.editBuffer(new EditMessage<IU>(EditType.REVOKE, iu));
			notifyListeners();
		}
		public void reset() {
			rightBuffer.setBuffer(null, null);
		}
	}
	
	protected void startChunk(String s) {
		ChunkIU chunk = new ChunkIU(s);
		chunk.preSynthesize();
		chunk.addUpdateListener(new IUUpdateListener() {
			@Override
			public void update(IU updatedIU) {
				Logger.getLogger(SynthesisModuleAdaptationUnitTest.class).info(
						"update message on IU " + updatedIU.toString() + 
						" with progress " + updatedIU.getProgress());
			}
		});
		myIUModule.addIUAndUpdate(chunk);
	}

}
