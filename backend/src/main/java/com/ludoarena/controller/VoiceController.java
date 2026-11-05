package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.IceServerDto;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.VoiceService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/voice")
public class VoiceController {
    private final VoiceService voice;

    public VoiceController(VoiceService voice) {
        this.voice = voice;
    }

    /** ICE servers for WebRTC (STUN, plus TURN with short-lived credentials when configured). Authenticated users only. */
    @GetMapping("/ice-servers")
    public ApiResponse<List<IceServerDto>> iceServers(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(voice.iceServers(me.id()));
    }
}
