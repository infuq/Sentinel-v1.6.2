/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;

/**
 * @author jialiang.linjl
 */
public class RateLimiterController implements TrafficShapingController {

    private final int maxQueueingTimeMs;
    private final double count; // 表示1秒允许通过count个请求, 则每个请求间隔 1/count (s) = 1/count * 1000 (ms)

    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        if (acquireCount <= 0) {
            return true;
        }
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        if (count <= 0) {
            return false;
        }



        long currentTime = TimeUtil.currentTimeMillis();
        // Calculate the interval between every two requests.
        // count: 表示1秒允许通过count个请求, 则每个请求间隔 1/count (s) = 1/count * 1000 (ms)
        // 以下表达式含义: 需要acquireCount个令牌的间隔时间
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);

        // Expected pass time of this request.
        // 满足`acquireCount个令牌`的时间点(expectedTime)
        long expectedTime = costTime + latestPassedTime.get(); // 表示时间点



        // 时间点小于当前时间, 表明系统比较空闲, 直接通过
        if (expectedTime <= currentTime) {
            // Contention may exist here, but it's okay. 翻译 这里可能存在争论,但没关系
            // 个人理解 : 假如count = 10, 表明1秒允许通过10个请求, 此时(请求1,acquireCount=6)和(请求2,acquireCount=9),
            // 两个请求同时执行67行代码, 在1秒的时间范围内, 两个请求都满足72行判断条件, 但实际通过了 6+9=15个请求
            latestPassedTime.set(currentTime);
            return true;
        } else {
            // |<------------------------- costTime --------------->|
            // |                            |<----- waitTime ------>|
            // |____________________________|_______________________|
            // ^                            ^                       ^
            // latestPassedTime             currentTime             expectedTime

            // Calculate the time to wait.
            // 计算需要等待的时长
            long waitTime = costTime + latestPassedTime.get() - TimeUtil.currentTimeMillis();
            // 需要等待的时间过长, 直接拒绝
            if (waitTime > maxQueueingTimeMs) {
                return false;
            } else { // 即 waitTime < maxQueueingTimeMs

                // 此处调用 addAndGet 方法返回的是最新的时间点, 名称叫做oldTime, 给人误解
                // 这里的oldTime 即 expectedTime含义
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    waitTime = oldTime - TimeUtil.currentTimeMillis();

                    // 91行表明 waitTime < maxQueueingTimeMs , 而经过95和97行运行之后, 存在 waitTime > maxQueueingTimeMs 的情况
                    // 解释 : 122 - 142行
                    if (waitTime > maxQueueingTimeMs) {
                        // 回滚更新
                        latestPassedTime.addAndGet(-costTime);
                        return false;
                    }
                    // in race condition waitTime may <= 0
                    if (waitTime > 0) {
                        // 休眠之后, 直接通过
                        Thread.sleep(waitTime);
                    }
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}


// 图1
// |<-------------------- costTime -------------------->|
// |                            |<------ waitTime ----->|                               waitTime < maxQueueingTimeMs
// |____________________________|_______________________|
// ^(A)                         ^(B)                    ^(C)
// latestPassedTime             currentTime             expectedTime


// 时间推移

// 图2
// |                <-------------------- costTime -------------------->|
// |                |                   |<--------- waitTime ---------->|               waitTime > maxQueueingTimeMs
// |________________|___________________|_______________________________|
//                  ^(D)                ^(E)                            ^(F)
//                  latestPassedTime   currentTime                      expectedTime


// 在图1中, waitTime < maxQueueingTimeMs .
// 随着时间推移, 存在竞争, latestPassedTime从A->D , currentTime从B->E ,  A->D的长度大于B->E的长度,
// 进而导致了waitTime长度增长, 造成waitTime > maxQueueingTimeMs

















