package com.nalbam.bot.task;

import com.amazonaws.services.sqs.model.Message;
import com.google.gson.Gson;
import com.nalbam.bot.domain.Queue;
import com.nalbam.bot.service.QueueService;
import com.nalbam.bot.service.SendService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class QueueTask {

    @Autowired
    private QueueService queueService;

    @Autowired
    private SendService sendService;

    @Scheduled(fixedRate = 1000)
    public void send() {
        Map<String, String> map = new HashMap<>();
        map.put("url", "http://nalbam-bot-prod.us-east-1.elasticbeanstalk.com/health");

        Queue queue = new Queue();
        queue.setType('2');
        queue.setDelay(0);
        queue.setData(map);

        this.queueService.send(queue)
                .exceptionally(e -> {
                    log.error("Queue send : {}", e.getMessage());
                    return null;
                })
                .thenApply(r -> {
                    //log.info("Queue send : {}", r.size());
                    return r;
                });
    }

    @Scheduled(fixedRate = 1000)
    public void receive() {
        this.queueService.receive()
                .exceptionally(e -> {
                    log.error("Queue receive : {}", e.getMessage());
                    return null;
                })
                .thenApply(r -> {
                    //log.info("Queue receive : {}", r.size());
                    receive(r);
                    return r;
                });
    }

    private void receive(final List<Message> list) {
        if (list.size() > 0) {
            log.info("Queue receive : {}", list.size());

            Queue queue;
            Long delay;

            for (final Message message : list) {
                queue = new Gson().fromJson(message.getBody(), Queue.class);

                log.info("Queue receive : {}", message.getMessageId());

                // 발송시간 = 예약시간 + delay
                delay = (queue.getDelay() * 1000) - new Date().getTime();

                if (queue.getReserved() != null) {
                    delay = delay + queue.getReserved().getTime();
                } else {
                    delay = delay + queue.getRegistered().getTime();
                }

                log.info("Queue receive : [{}] [delay:{}] {}", queue.getData(), milToSec(delay), queue.getTokens().size());

                if (delay < 1000) {
                    // 발송
                    this.sendService.send(queue);
                } else {
                    // 연기
                    this.queueService.changeVisibility(message.getReceiptHandle(), milToSec(delay));
                    continue;
                }

                // 삭제
                this.queueService.delete(message.getReceiptHandle());
            }
        }
    }

    private Integer milToSec(final Long mil) {
        return (mil.intValue() / 1000);
    }

}
