package com.chuqiyun.proxmoxveams.service.impl;

import com.chuqiyun.proxmoxveams.entity.Vncnode;
import com.chuqiyun.proxmoxveams.manager.VncNetworksChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @Author: 星禾
 * @Description: VNC 节点增删改提交后发布信任同步事件
 * @DateTime: 2026/11/2 21:20
 */
class VncnodeTrustEventTest {
    private VncnodeServiceImpl service;
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        service = spy(new VncnodeServiceImpl());
        publisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(service, "eventPublisher", publisher);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void successfulCrudPublishesCompleteSnapshotRefresh() {
        doReturn(true).when(service).save(any(Vncnode.class));
        doReturn(true).when(service).updateById(any(Vncnode.class));
        doReturn(true).when(service).removeById(1L);

        assertTrue(service.addVncnode(new Vncnode()));
        assertTrue(service.updateVncnode(new Vncnode()));
        assertTrue(service.deleteVncnode(1L));

        verify(publisher, times(3)).publishEvent(any(VncNetworksChangedEvent.class));
    }

    @Test
    void failedCrudDoesNotPublishConfigurationThatWasNotStored() {
        doReturn(false).when(service).save(any(Vncnode.class));
        doReturn(false).when(service).updateById(any(Vncnode.class));
        doReturn(false).when(service).removeById(1L);

        assertFalse(service.addVncnode(new Vncnode()));
        assertFalse(service.updateVncnode(new Vncnode()));
        assertFalse(service.deleteVncnode(1L));

        verifyNoInteractions(publisher);
    }

    @Test
    void invalidProxySourceIsRejectedBeforePersistenceOrTrustPublication() {
        for (String invalid : new String[]{"0.0.0.0/0", "::/0", "relay.example", "192.0.2.5:7600",
                "192.0.2.5/33", "2001:db8::/129", "192.0.2.5/24/32", ",,,"}) {
            Vncnode node = new Vncnode();
            node.setProxySourceIps(invalid);
            assertThrows(IllegalArgumentException.class, () -> service.addVncnode(node));
            assertThrows(IllegalArgumentException.class, () -> service.updateVncnode(node));
        }
        verify(service, never()).save(any(Vncnode.class));
        verify(service, never()).updateById(any(Vncnode.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void canonicalizesExplicitSourcesAndPreservesOmittedOrClearedFieldMeaning() {
        doReturn(true).when(service).save(any(Vncnode.class));
        doReturn(true).when(service).updateById(any(Vncnode.class));
        Vncnode explicit = new Vncnode();
        explicit.setProxySourceIps("192.0.2.29/24,192.0.2.0/24 198.51.100.5");
        assertTrue(service.addVncnode(explicit));
        assertEquals("192.0.2.0/24\n198.51.100.5/32", explicit.getProxySourceIps());

        Vncnode omitted = new Vncnode();
        assertTrue(service.updateVncnode(omitted));
        assertNull(omitted.getProxySourceIps());
        Vncnode clear = new Vncnode();
        clear.setProxySourceIps(" \n");
        assertTrue(service.updateVncnode(clear));
        assertEquals("", clear.getProxySourceIps());
    }

    @Test
    void publishesOnlyAfterTransactionCommits() {
        doReturn(true).when(service).save(any(Vncnode.class));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertTrue(service.addVncnode(new Vncnode()));
        verifyNoInteractions(publisher);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(publisher).publishEvent(any(VncNetworksChangedEvent.class));
    }

    @Test
    void rolledBackTransactionNeverPublishesTrustSnapshot() {
        doReturn(true).when(service).save(any(Vncnode.class));
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertTrue(service.addVncnode(new Vncnode()));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(publisher);
    }

    @Test
    void schedulingFailureDoesNotReportCommittedNodeUpdateAsFailed() {
        doReturn(true).when(service).updateById(any(Vncnode.class));
        doThrow(new IllegalStateException("executor unavailable")).when(publisher)
                .publishEvent(any(VncNetworksChangedEvent.class));

        assertTrue(service.updateVncnode(new Vncnode()));
    }
}
