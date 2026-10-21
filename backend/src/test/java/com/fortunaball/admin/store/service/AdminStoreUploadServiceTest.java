package com.fortunaball.admin.store.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fortunaball.admin.auth.domain.AdminPrincipal;
import com.fortunaball.admin.log.service.AdminActionRecorder;
import com.fortunaball.admin.store.dto.StoreUploadApplyResult;
import com.fortunaball.admin.store.dto.StoreUploadDetail;
import com.fortunaball.admin.store.dto.StoreUploadResult;
import com.fortunaball.admin.store.dto.StoreUploadRow;
import com.fortunaball.admin.store.repository.StoreUploadRepository;
import com.fortunaball.admin.store.service.StoreDiffCalculator.ExistingStore;
import com.fortunaball.common.exception.BusinessException;
import com.fortunaball.store.importer.StoreAddressParser;
import com.fortunaball.store.importer.StoreImportService;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminStoreUploadServiceTest {

    private static final String HEADER = "draw,rank,shop_id,shop_name,status,region,road_addr,jibun_addr,auto_manual,lat,lon\n";
    private static final String CSV = HEADER
            + "1246,1,1001,가게A,정상,서울,서울 중구 퇴계로 1,서울 중구 장충동 1,자동,37.500000,127.000000\n"
            + "1245,2,1002,가게B,폐점,서울,서울 중구 퇴계로 2,서울 중구 장충동 2,,37.510000,127.010000\n";

    private final StoreUploadRepository repo = mock(StoreUploadRepository.class);
    private final StoreUploadFiles files = mock(StoreUploadFiles.class);
    private final StoreImportService importService = mock(StoreImportService.class);
    private final AdminActionRecorder recorder = mock(AdminActionRecorder.class);
    private final AdminStoreUploadService service = new AdminStoreUploadService(repo, files, importService, recorder);

    @BeforeEach
    void login() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AdminPrincipal(7L, "admin", "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static StoreUploadRow staged(long id) {
        return new StoreUploadRow(id, "FIRST_SECOND_CSV", "a.csv", LocalDate.of(2026, 10, 17), 2, 2, 0, "STAGED", 7L,
                null, LocalDateTime.of(2026, 10, 21, 15, 0), null);
    }

    private void roundsExist() {
        when(repo.findExistingRounds(1245, 1246)).thenReturn(Set.of(1245, 1246));
    }

    // ---------------------------------------------------------------- 업로드

    @Test
    void 업로드하면_STAGED로_저장하고_파일을_보관한다() throws IOException {
        roundsExist();
        when(repo.loadExistingStores()).thenReturn(Map.of());
        when(repo.loadHistoryCounts(1245, 1246)).thenReturn(Map.of());
        when(repo.drawDate(1246)).thenReturn(LocalDate.of(2026, 10, 17));
        when(repo.insertUpload(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyString(), anyLong()))
                .thenReturn(5L);

        StoreUploadResult r = service.upload("C:\\fake\\a.csv", bytes(CSV), "first_second_csv");

        assertEquals(5L, r.uploadId());
        assertEquals("STAGED", r.status());
        assertEquals("a.csv", r.fileName());               // 경로는 버린다
        assertEquals(2, r.validation().totalRows());
        assertEquals(2, r.validation().storeCount());
        assertEquals(1245, r.validation().minRound());
        assertEquals(1246, r.validation().maxRound());
        assertEquals(2, r.summary().newStores());
        verify(repo).insertUpload("FIRST_SECOND_CSV", "a.csv", LocalDate.of(2026, 10, 17), 2, 2, 0, "STAGED", 7L);
        verify(files).save(eq(5L), any());
        verify(recorder).record(eq(7L), eq("UPLOAD_STORE_FILE"), eq("store_data_upload"), eq(5L), anyString());
    }

    @Test
    void 지원하지_않거나_모르는_종류는_거절() {
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), "STORE_LIST"));
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), "FIRST_SECOND_EXCEL"));
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), "HACK"));
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), null));
        verify(repo, never()).insertUpload(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyString(), anyLong());
    }

    @Test
    void 파일_자체가_잘못되면_거절() {
        assertThrows(BusinessException.class, () -> service.upload("a.csv", new byte[0], "FIRST_SECOND_CSV"));
        assertThrows(BusinessException.class, () -> service.upload("a.csv", null, "FIRST_SECOND_CSV"));
        assertThrows(BusinessException.class, () -> service.upload("a.xlsx", bytes(CSV), "FIRST_SECOND_CSV"));
        assertThrows(BusinessException.class,
                () -> service.upload("a.csv", new byte[(int) AdminStoreUploadService.MAX_FILE_BYTES + 1], "FIRST_SECOND_CSV"));
    }

    @Test
    void 열이_없거나_값이_잘못되면_거절하고_이력을_만들지_않는다() {
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes("draw,rank\n1,1\n"), "FIRST_SECOND_CSV"));
        String badRank = HEADER + "1246,3,1001,가게A,정상,서울,서울 중구 퇴계로 1,서울 중구 장충동 1,자동,37.5,127.0\n";
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(badRank), "FIRST_SECOND_CSV"));
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(HEADER), "FIRST_SECOND_CSV"));
        verify(repo, never()).insertUpload(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyString(), anyLong());
    }

    @Test
    void 회차_데이터에_없는_회차가_있으면_거절() {
        when(repo.findExistingRounds(1245, 1246)).thenReturn(Set.of(1246));   // 1245 없음
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), "FIRST_SECOND_CSV"));
        verify(repo, never()).insertUpload(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyString(), anyLong());
    }

    @Test
    void 파일_보관에_실패하면_FAILED로_남기고_오류() throws IOException {
        roundsExist();
        when(repo.loadExistingStores()).thenReturn(Map.of());
        when(repo.loadHistoryCounts(anyInt(), anyInt())).thenReturn(Map.of());
        when(repo.insertUpload(anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyString(), anyLong()))
                .thenReturn(6L);
        doThrow(new IOException("disk full")).when(files).save(eq(6L), any());
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), "FIRST_SECOND_CSV"));
        verify(repo).markFailed(eq(6L), anyString());
    }

    // ---------------------------------------------------------------- 반영

    @Test
    void 반영하면_새_판매점에만_처음_확인된_업로드를_적고_APPLIED로_바꾼다() throws IOException {
        when(repo.lockUpload(5L)).thenReturn(Optional.of(staged(5L)));
        when(files.read(5L)).thenReturn(bytes(CSV));
        StoreAddressParser.Region reg = StoreAddressParser.parse("서울", "서울 중구 퇴계로 1", "서울 중구 장충동 1");
        when(repo.loadExistingStores()).thenReturn(Map.of("1001", new ExistingStore(1, "가게A", "서울 중구 퇴계로 1",
                "서울 중구 장충동 1", reg.sido(), reg.sigungu(), new BigDecimal("37.5"), new BigDecimal("127.0"), false)));
        when(importService.importRows(any())).thenReturn(new StoreImportService.Result(2, 1, 2, 2, 1245, 1246,
                LocalDate.of(2026, 10, 17)));
        when(repo.countGeocodePending()).thenReturn(0);

        StoreUploadApplyResult r = service.apply(5L);

        assertEquals("APPLIED", r.status());
        assertEquals(1, r.newStores());                       // 1002 만 새 판매점
        assertEquals(2, r.stores());
        verify(repo).stampFirstSeen(eq(5L), argThat(c -> c.size() == 1 && c.contains("1002")), any());
        verify(repo).stampSummaryUpload(eq(5L), argThat(c -> c.size() == 2));
        verify(repo).markApplied(eq(5L), any());
        verify(recorder).record(eq(7L), eq("APPLY_STORE_FILE"), eq("store_data_upload"), eq(5L), anyString());
    }

    @Test
    void 이미_반영됐거나_취소된_업로드는_반영할_수_없다() {
        StoreUploadRow applied = new StoreUploadRow(5L, "FIRST_SECOND_CSV", "a.csv", null, 2, 2, 0, "APPLIED", 7L, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(repo.lockUpload(5L)).thenReturn(Optional.of(applied));
        assertThrows(BusinessException.class, () -> service.apply(5L));
        verify(importService, never()).importRows(any());
    }

    @Test
    void 없는_업로드는_반영도_취소도_조회도_NOT_FOUND() {
        when(repo.lockUpload(9L)).thenReturn(Optional.empty());
        when(repo.find(9L)).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.apply(9L));
        assertThrows(BusinessException.class, () -> service.cancel(9L));
        assertThrows(BusinessException.class, () -> service.detail(9L, null, null, null));
    }

    @Test
    void 반영_중_오류가_나면_APPLIED로_바꾸지_않는다() throws IOException {
        when(repo.lockUpload(5L)).thenReturn(Optional.of(staged(5L)));
        when(files.read(5L)).thenReturn(bytes(CSV));
        when(repo.loadExistingStores()).thenReturn(Map.of());
        when(importService.importRows(any())).thenThrow(new IllegalStateException("draw_round 에 없는 회차"));
        assertThrows(BusinessException.class, () -> service.apply(5L));
        verify(repo, never()).markApplied(anyLong(), any());
    }

    @Test
    void 파일이_서버에_없으면_반영_불가() throws IOException {
        when(repo.lockUpload(5L)).thenReturn(Optional.of(staged(5L)));
        when(files.read(5L)).thenThrow(new IOException("없음"));
        assertThrows(BusinessException.class, () -> service.apply(5L));
        verify(importService, never()).importRows(any());
    }

    // ---------------------------------------------------------------- 취소

    @Test
    void 취소하면_상태를_바꾸고_파일을_지운다() {
        when(repo.lockUpload(5L)).thenReturn(Optional.of(staged(5L)));
        when(repo.find(5L)).thenReturn(Optional.of(staged(5L)));
        service.cancel(5L);
        verify(repo).markCanceled(5L);
        verify(files).delete(5L);
        verify(recorder).record(eq(7L), eq("CANCEL_STORE_FILE"), eq("store_data_upload"), eq(5L), anyString());
    }

    @Test
    void 반영된_업로드는_취소할_수_없다() {
        StoreUploadRow applied = new StoreUploadRow(5L, "FIRST_SECOND_CSV", "a.csv", null, 2, 2, 0, "APPLIED", 7L, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(repo.lockUpload(5L)).thenReturn(Optional.of(applied));
        assertThrows(BusinessException.class, () -> service.cancel(5L));
        verify(repo, never()).markCanceled(anyLong());
    }

    // ---------------------------------------------------------------- 비교 결과 · 이력

    @Test
    void 비교_결과는_구분으로_거르고_쪽으로_나눈다() throws IOException {
        when(repo.find(5L)).thenReturn(Optional.of(staged(5L)));
        when(files.read(5L)).thenReturn(bytes(CSV));
        when(repo.loadExistingStores()).thenReturn(Map.of());
        when(repo.loadHistoryCounts(1245, 1246)).thenReturn(Map.of());

        StoreUploadDetail all = service.detail(5L, null, 0, 1);
        assertEquals(2, all.items().totalCount());
        assertEquals(1, all.items().items().size());
        assertTrue(all.items().hasNext());

        StoreUploadDetail changed = service.detail(5L, "changed", null, null);
        assertEquals(0, changed.items().totalCount());

        StoreUploadDetail isNew = service.detail(5L, "NEW", null, null);
        assertEquals(2, isNew.items().totalCount());
    }

    @Test
    void 끝난_업로드의_비교_결과는_저장된_건수만_보여_준다() throws IOException {
        StoreUploadRow applied = new StoreUploadRow(5L, "FIRST_SECOND_CSV", "a.csv", null, 2, 3, 4, "APPLIED", 7L, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(repo.find(5L)).thenReturn(Optional.of(applied));
        StoreUploadDetail d = service.detail(5L, null, null, null);
        assertEquals(3, d.summary().newStores());
        assertEquals(4, d.summary().changedStores());
        assertTrue(d.items().items().isEmpty());
        verify(files, never()).read(anyLong());
    }

    @Test
    void 잘못된_구분이나_상태는_거절() {
        when(repo.find(5L)).thenReturn(Optional.of(staged(5L)));
        assertThrows(BusinessException.class, () -> service.detail(5L, "ZZZ", null, null));
        assertThrows(BusinessException.class, () -> service.history(null, null, "WHAT", null, null));
    }

    @Test
    void 로그인_없이는_모두_거절() {
        SecurityContextHolder.clearContext();
        assertThrows(BusinessException.class, () -> service.upload("a.csv", bytes(CSV), "FIRST_SECOND_CSV"));
        assertThrows(BusinessException.class, () -> service.apply(5L));
        assertThrows(BusinessException.class, () -> service.cancel(5L));
    }
}
