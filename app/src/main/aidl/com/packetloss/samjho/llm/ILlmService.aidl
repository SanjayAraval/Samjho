package com.packetloss.samjho.llm;

import android.os.Bundle;
import com.packetloss.samjho.llm.ILlmListener;

/** The app's only view of the language model, which runs in the :llm process. */
interface ILlmService {
    /**
     * Starts loading the model (safe to call again) and reports progress to [listener]. Returns at once;
     * loading may take minutes the first time, while the backends are tried.
     */
    void load(String modelPath, ILlmListener listener);

    /**
     * Blocking, one prompt at a time. Keys: "ok", "text", "error", "backend", "decodeTps", "prefillTps",
     * "millis". Never throws for a model problem: "ok" is false and "error" says why.
     */
    Bundle complete(String prompt, int maxTokens);

    /** Stops the model process. Used when a call has run past its time limit. */
    oneway void kill();
}
