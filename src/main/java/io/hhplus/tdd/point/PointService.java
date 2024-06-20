package io.hhplus.tdd.point;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;

@Service
public class PointService {
    private static final Logger log = LoggerFactory.getLogger(PointService.class);
    PointHistoryTable pointHistoryTable;
    UserPointTable userPointTable;

    private final UserPointLock userLock = new UserPointLock();
    
    public PointService(PointHistoryTable pointHistoryTable, UserPointTable userPointTable) {
        this.pointHistoryTable = pointHistoryTable;
        this.userPointTable = userPointTable;
    }

    public UserPoint getUserPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    public List<PointHistory> getPointHistory(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    public UserPoint chargeUserPoint(long userId, long chargePoint){
        if(chargePoint <= 0) 
            throw new RuntimeException("0이하의 포인트는 충전할 수 없습니다.");

        try {
            userLock.waitForLock(userId);
        } catch (Exception e) {
            log.error("userLock error", e);
            throw new RuntimeException(e.getMessage());
        }

        UserPoint chargeRes;
        try{
            long currentPoint = userPointTable.selectById(userId).point();
            chargeRes = userPointTable.insertOrUpdate(userId, currentPoint + chargePoint);
    
            pointHistoryTable.insert(userId, chargePoint, TransactionType.CHARGE, System.currentTimeMillis());
        } finally {
            userLock.releaseLock(userId);
        }

        return chargeRes;
    }

    public UserPoint useUserPoint(long userId, long usePoint) {
        if(usePoint <= 0) 
            throw new RuntimeException("0보다 적은 포인트는 사용할 수 없습니다.");
        
        try {
            userLock.waitForLock(userId);
        } catch (Exception e) {
            log.error("userLock error", e);
            throw new RuntimeException(e.getMessage());
        }

        UserPoint useRes;
        try{
            long currentPoint = userPointTable.selectById(userId).point();
            if(currentPoint < usePoint)
                throw new RuntimeException("포인트가 부족합니다.");
            useRes = userPointTable.insertOrUpdate(userId, currentPoint - usePoint);
            pointHistoryTable.insert(userId, usePoint, TransactionType.USE, System.currentTimeMillis());
        } finally{
            userLock.releaseLock(userId);
        }
        
        return useRes;
    }
}
