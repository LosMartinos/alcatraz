package com.alcatraz.web.dto;

import java.util.List;

public record GameStatusResponse(String status, List<PlayerInfo> players) {}
