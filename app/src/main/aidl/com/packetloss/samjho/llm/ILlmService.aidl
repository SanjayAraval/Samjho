package com.packetloss.samjho.llm;

interface ILlmService {
    String ask(String prompt);
    String getStatus();
    String getBackend();
}
