package com.developers.live.developers.mentoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class RoomEnterResponseDto {
    private String code;
    private String msg;
    private String data;
}