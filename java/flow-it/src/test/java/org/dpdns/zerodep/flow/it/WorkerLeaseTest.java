package org.dpdns.zerodep.flow.it;

import org.dpdns.zerodep.flow.core.store.query.LeasedWorkerIds;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.query.Sql;
import org.dpdns.zerodep.query.exec.Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 雪花的机器号在 flow_worker 表里租用（docs/decisions/0001 的"雪花的机器号"） */
@DisplayName("雪花机器号的租约")
class WorkerLeaseTest {
    private Database db;

    @BeforeEach
    void freshTables() {
        LiveMysql.recreateTables();
        db = Sql.database(LiveMysql.dataSource());
    }

    @Test
    void 同时在用的进程拿到不同的号_放掉的号给下一个进程() throws Exception {
        LeasedWorkerIds first = new LeasedWorkerIds(db, Clock.systemUTC());
        LeasedWorkerIds second = new LeasedWorkerIds(db, Clock.systemUTC());
        assertEquals(0, first.workerId(1024));
        assertEquals(1, second.workerId(1024));
        assertEquals(0, first.workerId(1024), "a process keeps its id");

        first.close();
        try (LeasedWorkerIds third = new LeasedWorkerIds(db, Clock.systemUTC())) {
            assertEquals(0, third.workerId(1024), "the released id is reused");
        }
        second.close();
    }
}
