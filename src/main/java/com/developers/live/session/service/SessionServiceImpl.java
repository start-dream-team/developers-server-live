package com.developers.live.session.service;

import com.developers.live.mentoring.entity.Schedule;
import com.developers.live.mentoring.repository.ScheduleRepository;
import com.developers.live.session.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Log4j2
public class SessionServiceImpl implements SessionService {

    private final RedisTemplate<String, Object> redisTemplate; // 실제 서비스 redisTemplate
    private final ScheduleRepository scheduleRepository; // 스케쥴에 해당하는 사용자 확인

    @Override
    public SessionRedisSaveResponse enter(SessionRedisSaveRequest request) {
        String roomName = request.getRoomName();
        String userName = request.getUserName();
        Long expireTime = request.getTime();

        // 스케쥴 기반으로, 스케쥴에 해당 mentee가 등록된 사용자인지 여부 체크
        Optional<Schedule> schedule = scheduleRepository.findById(request.getScheduleId());
        if (schedule.isPresent()) {
            if (schedule.get().getMentorId().equals(request.getUserId())) {
                return getSessionRedisSaveResponse(roomName, userName, expireTime);
            } else if(schedule.get().getMenteeId().equals(request.getUserId())){
                return getSessionRedisSaveResponse(roomName, userName, expireTime);
            }
            else {
                log.error("스케쥴에 예약한 사용자만 입장할 수 있습니다! 요청한 사용자: " + request.getUserName());
                throw new IllegalArgumentException(request.getUserName() + " 사용자는 해당 방에 입장할 수 없습니다");
            }
        } else {
            log.error("스케쥴 정보 오류!");
            throw new InvalidDataAccessApiUsageException("해당 일정은 존재하지 않습니다!");
        }
    }

    private SessionRedisSaveResponse getSessionRedisSaveResponse(String roomName, String userName, Long expireTime) {
        try {
            // 1. Redis 데이터 삽입 로직 수행
            redisTemplate.opsForSet().add(roomName, userName);
            redisTemplate.expire(roomName, Duration.ofMinutes(expireTime));
            redisTemplate.opsForHash().put("rooms", roomName, userName);

            // 2. 삽입한 데이터 클라이언트에 전달
            SessionRedisSaveResponse response = SessionRedisSaveResponse.builder()
                    .code(HttpStatus.OK.toString())
                    .msg("정상적으로 처리하였습니다.")
                    .data(redisTemplate.opsForSet().members(roomName).toString()).build();
            log.info("Redis 세션 저장! " + roomName + "에 " + userName + "저장!");
            return response;
        } catch (Exception e) {
            log.error("Redis 세션 저장 오류! 방 정보: " + roomName + " 사용자 정보: " + userName, e);
            throw new RedisException("Redis 세션 저장에 요류가 발생하였습니다. ", e);
        }
    }

    @Override
    public SessionRedisFindAllResponse list() {
        try {
            // 1. Redis에서 모든 채팅방 정보를 가져온다.
            Set<Object> rooms = redisTemplate.opsForHash().keys("rooms");
            Map<Object, Object> roomsInfo = new HashMap();
            for (Object room : rooms) {
                roomsInfo.put(room, redisTemplate.opsForSet().members(room.toString()));
            }

            if (rooms == null || rooms.isEmpty()) {
                log.error("Redis 세션 전체 출력 오류! ");
                throw new InvalidDataAccessApiUsageException("현재 Redis 세션이 없습니다. ");
            }

            // Redis 반환 값 객체 변환
            ObjectMapper mapper = new ObjectMapper();
            String jsonData = mapper.writeValueAsString(roomsInfo);

            // 2. Redis에 있는 모든 채팅방 정보를 응답해야 한다.
            SessionRedisFindAllResponse response = SessionRedisFindAllResponse.builder()
                    .code(HttpStatus.OK.toString())
                    .msg("정상적으로 처리되었습니다.")
                    .data(jsonData)
                    .build();
            log.info("Redis 세션 불러오기 완료!");
            return response;
        } catch (RedisException e) {
            log.error("Redis 세션 전체 출력 오류! ", e);
            throw new IllegalArgumentException("Redis 세션 전체를 불러오는데 오류가 발생했습니다. ", e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public SessionRedisRemoveResponse remove(SessionRedisRemoveRequest request) {
        String roomName = request.getRoomName();

        Optional<Schedule> schedule = scheduleRepository.findById(request.getScheduleId());
        if (schedule.isPresent()) {
            if (schedule.get().getMentorId().equals(request.getUserId())) {
                try {
                    if (!redisTemplate.opsForHash().hasKey("rooms", roomName)) {
                        log.error("Redis 세션 삭제 오류! ", roomName);
                        throw new IllegalArgumentException("Redis에서 해당 세션은 존재하지 않습니다. ");
                    }
                    // 1. Redis에 request.getRoomId()를 가지고 가서 해당하는 데이터 삭제
                    redisTemplate.opsForHash().delete("rooms", roomName);

                    // 2. 삭제한 데이터
                    SessionRedisRemoveResponse response = SessionRedisRemoveResponse.builder()
                            .code(HttpStatus.OK.toString())
                            .msg("정상적으로 처리되었습니다.")
                            .data(String.valueOf(redisTemplate.delete(roomName)))
                            .build();
                    log.info("Redis 세션 삭제 완료! " + roomName + "에 대한 세션 삭제!");
                    return response;
                } catch (Exception e) {
                    log.error("Redis 세션 삭제 오류! ", e);
                    throw new IllegalArgumentException("Redis에서 세션을 삭제하는데 오류가 발생했습니다. ", e);
                }
            } else {
                log.error("멘토 방 삭제 오류!");
                throw new IllegalArgumentException("멘토만 방을 삭제할 수 있습니다!");
            }
        } else {
            log.error("스케쥴 정보 오류!");
            throw new InvalidDataAccessApiUsageException("해당 일정은 존재하지 않습니다!");
        }
    }
}
