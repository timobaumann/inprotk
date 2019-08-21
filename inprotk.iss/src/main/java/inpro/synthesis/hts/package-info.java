/**
 * Incremental HMM-based speech synthesis for InproTK. 
<pre>
_____________________________________________________________
  _                                         _           _   
 (_)_ __  _ __  _ __ ___    _ __  _ __ ___ (_) ___  ___| |_ 
 | | '_ \| '_ \| '__/ _ \  | '_ \| '__/ _ \| |/ _ \/ __| __|
 | | | | | |_) | | | (_) | | |_) | | | (_) | |  __/ (__| |_ 
 |_|_| |_| .__/|_|  \___/  | .__/|_|  \___// |\___|\___|\__|
         |_|               |_|           |__/               
_____________________________________________________________
</pre>
 * This package
 * bridges between MaryTTS's HMM-based speech synthesis module
 * (which is based on HTS) and the IU world in InproTK.
 * <p>
 * Specifically, {@link inpro.synthesis.hts.InteractiveHTSEngine} is a slight extension of Mary's
 * {@link marytts.modules.HTSEngine} which additionally makes accessible 
 * a list of {@link marytts.htsengine.HTSModel}s,
 * one for each speech segment in the utterance that was most recently synthesized.
 * {@link inpro.synthesis.MaryAdapter5internal} adds the HTSModels to the IU structure 
 * via {@link inpro.incremental.util.TTSUtil#wordIUsFromMaryXML(java.io.InputStream, java.util.List)}.
 * (In other words, HMM state selection is currently performed non-incrementally before 
 * synthesis of the utterance starts.) 
 * <p>
 * Speech synthesis proper is performed from a "crawling vocoder" that performs 
 * synthesis incrementally (but monotonously, i.e., not supporting revokes).
 * Unfortunately, HTS/Mary's data layout is orthogonal to that needed for incremental processing, 
 * necessitating some data-shuffling: Mary/HTS assumes one fixed-length array of HMM observation 
 * <emph>per feature</emph>, whereas incremental processing requires <emph>all features</emph> 
 * at a time with the number of frames being unknown during processing. Thus, the original 
 * layout it converted to suit stream processing: 
 * <ul>
 * <li>individual parameters for a certain frame are combined to {@link inpro.synthesis.hts.FullPFeatureFrame}
 * <li>{@code FullPFeatureFrame}s are combined to {@link inpro.synthesis.hts.FullPStream} objects that provide 
 * a list interface 
 * <li>{@link inpro.synthesis.hts.HTSFullPStream} implements {@code FullPStream} based on HTS/Mary's data structures
 * (i.e., implementing the by-column access based on the by-row data provided by HTS/Mary)
 * <li>{@link inpro.synthesis.hts.IUBasedFullPStream} implements {@code FullPStream} by following the forward-pointing 
 * same-level links (SLLs) of {@link inpro.incremental.unit.SysSegmentIU}s, polling them for their 
 * {@code FullPFeatureFrame}s one-by-one.
 * </ul>
 * <p>
 * {@link inpro.synthesis.hts.PHTSParameterGeneration} performs the HMM observation optimization based on 
 * Mary's {@link marytts.htsengine.HTSParameterGeneration} and transforms the data layout 
 * to suit stream-based processing as outlined above.
 * {@link inpro.incremental.unit.SysSegmentIU}s use {@code inpro.synthesis.hts.PHTSParameterGeneration#buildFullPStreamFor(java.util.List)} 
 * to optimize their associated features frames (which are returned as {@link inpro.synthesis.hts.FullPStream}s). 
 * {@link inpro.incremental.unit.SysSegmentIU}s use a context of two segments left and right
 * (as proposed by Dutoit et al., 2011).
 * <p>
 * Finally {@link inpro.synthesis.hts.VocodingAudioStream} consumes a {@link inpro.synthesis.hts.FullPStream}
 * and turns into speech samples that can be sent to the sound card.
 * 
 * @author Timo Baumann
 *
 */
package inpro.synthesis.hts;
