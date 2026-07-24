package com.cc.whatif.storytellermcp.persistence.story.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum VirtueApproach {
    PLOT_DRIVEN("plot-driven"),
    MOMENT_BASED("moment-based");

    @Getter
    private final String description;
}
