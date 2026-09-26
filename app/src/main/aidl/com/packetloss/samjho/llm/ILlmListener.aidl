package com.packetloss.samjho.llm;

import android.os.Bundle;

/** Progress from the model process. One-way, so the model process never waits on the app. */
oneway interface ILlmListener {
    /** Keys: "state" (LOADING, READY or FAILED), "detail", "backend", "decodeTps", "summary". */
    void onStatus(in Bundle status);
}
