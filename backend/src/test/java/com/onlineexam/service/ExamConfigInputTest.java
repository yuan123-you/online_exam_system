package com.onlineexam.service;
import com.onlineexam.StoreService;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class ExamConfigInputTest {
  StoreService storage;StoreService.Store view;EntityCrudService service;
  @BeforeEach void setup(){
    storage=mock(StoreService.class);view=new StoreService.Store();when(storage.readStore()).thenReturn(view);
    view.users.add(Map.of("id","t1","role","teacher"));view.classes.add(Map.of("id","c1"));
    view.questions.add(Map.of("id","q1","teacherId","t1","score",10));
    view.papers.add(Map.of("id","p1","teacherId","t1","name","Paper","durationMinutes",30,"passScore",6,"totalScore",10,"questionIds",List.of("q1")));
    service=new EntityCrudService(storage,mock(AuthService.class),mock(SystemLogService.class));
  }
  Map<String,Object> paper(){return new LinkedHashMap<>(Map.of("name","New","durationMinutes",30,"passScore",6,"questionIds",List.of("q1")));}
  Map<String,Object> exam(){return new LinkedHashMap<>(Map.of("name","New","paperId","p1","targetClassIds",List.of("c1"),"startTime","2026-10-10T00:00:00Z","endTime","2026-10-10T01:00:00Z","antiCheatLimit",3));}
  void reject(String type,Map<String,Object> input){assertEquals(HttpStatus.BAD_REQUEST,service.createEntity("t1",Map.of("entity",type,"record",input)).getStatusCode());verify(storage,never()).createRecord(anyString(),anyMap());verify(storage,never()).saveRecord(anyString(),anyMap());}
  static Stream<Object> badDurations(){return Stream.of(0,-1,2.5,4294967297L,"invalid",(Object)null);}
  @ParameterizedTest @MethodSource("badDurations") void durationMustBePositiveExactInt(Object raw){var r=paper();r.put("durationMinutes",raw);reject("papers",r);}
  @Test void missingDurationCannotReachSqlAsZero(){var r=paper();r.remove("durationMinutes");reject("papers",r);}
  static Stream<Object> badNonnegativeInts(){return Stream.of(-1,2.5,4294967297L,"bad",(Object)null);}
  @ParameterizedTest @MethodSource("badNonnegativeInts") void passScoreCannotBeNegativeTruncatedOrImplicitZero(Object raw){var r=paper();r.put("passScore",raw);reject("papers",r);}
  @ParameterizedTest @MethodSource("badNonnegativeInts") void antiCheatCannotBeNegativeTruncatedOrImplicitZero(Object raw){var r=exam();r.put("antiCheatLimit",raw);reject("exams",r);}
  static Stream<Arguments> invalidTimes(){return Stream.of(Arguments.of("invalid","2026-10-10T01:00:00Z"),Arguments.of("2026-10-10T00:00:00Z","bad"),
      Arguments.of("2026-10-10T00:00:00Z","2026-10-10T00:00:00Z"),Arguments.of("2026-10-10T01:00:00Z","2026-10-10T00:00:00Z"),
      Arguments.of("2026-10-10T00:00:00.000000001Z","2026-10-10T00:00:00.000000002Z"));}
  @ParameterizedTest @MethodSource("invalidTimes") void invalidOrUnrepresentableIntervalsRejectBeforeWrite(String start,String end){var r=exam();r.put("startTime",start);r.put("endTime",end);reject("exams",r);}
  @Test void requiredTimesCannotBeOmitted(){var r=exam();r.remove("startTime");reject("exams",r);r=exam();r.remove("endTime");reject("exams",r);}
  @ParameterizedTest @ValueSource(ints={1,481,Integer.MAX_VALUE}) void validPositiveDurationHasNoInvented480Cap(int value){var r=paper();r.put("durationMinutes",value);assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","papers","record",r)).getStatusCode());}
  @Test void existingZeroDefaultsAndPassScoreAboveTotalAreNotSilentlyRedesigned(){var r=paper();r.remove("passScore");assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","papers","record",r)).getStatusCode());
    r=paper();r.put("passScore",100);assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","papers","record",r)).getStatusCode());
    var e=exam();e.remove("antiCheatLimit");assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","exams","record",e)).getStatusCode());}
  @Test void embeddedTimezoneMarkerIsNotRemovedToManufactureValidTime(){var r=exam();r.put("startTime","2026-10-10TZ00:00:00");reject("exams",r);}
  @Test void validOffsetMinuteTimeKeepsUtcMeaningInsteadOfLosingTimezone(){var r=exam();r.put("startTime","2026-10-10T00:00Z");r.put("endTime","2026-10-10T01:00Z");
    assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","exams","record",r)).getStatusCode());
    var captured=org.mockito.ArgumentCaptor.forClass(Map.class);verify(storage).createRecord(eq("exams"),captured.capture());assertEquals("2026-10-10T00:00:00Z",captured.getValue().get("startTime"));}
  @Test void localOverlapKeepsExistingTimestampWriterResolution(){
    var original=TimeZone.getDefault();try{
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));var r=exam();r.put("startTime","2026-11-01T01:30:00");r.put("endTime","2026-11-01T02:00:00");
      assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","exams","record",r)).getStatusCode());
      var captured=org.mockito.ArgumentCaptor.forClass(Map.class);verify(storage).createRecord(eq("exams"),captured.capture());
      assertEquals(java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse("2026-11-01T01:30:00")).toInstant().toString(),captured.getValue().get("startTime"));
      assertEquals("2026-11-01T06:30:00Z",captured.getValue().get("startTime"));
    }finally{TimeZone.setDefault(original);}
  }
  @ParameterizedTest @ValueSource(strings={"+10000-01-01T00:00:00Z","0999-01-01T00:00:00Z"})
  void parseableButUnstorableDateMustRejectBeforeWrite(String start){var r=exam();r.put("startTime",start);r.put("endTime",java.time.Instant.parse(start).plusSeconds(3600).toString());reject("exams",r);}
  @Test void databaseRangeAccountsForWriterTimezoneNotOnlyInputYear(){
    var original=TimeZone.getDefault();try{
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));var r=exam();r.put("startTime","9999-12-31T23:00:00Z");r.put("endTime","9999-12-31T23:30:00Z");reject("exams",r);
    }finally{TimeZone.setDefault(original);}
  }
  @Test void representableUpperBoundaryInUtcStillSupported(){
    var original=TimeZone.getDefault();try{
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));var r=exam();r.put("startTime","9999-12-31T23:00:00Z");r.put("endTime","9999-12-31T23:30:00Z");
      assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","exams","record",r)).getStatusCode());
    }finally{TimeZone.setDefault(original);}
  }
  @Test void normalizedTimesAreDetachedAndMatchWriterPrecision(){var r=exam();r.put("startTime","2026-10-10T00:00:00.123456789+08:00");r.put("endTime","2026-10-10T01:00:00.123456789+08:00");
    assertEquals(HttpStatus.OK,service.createEntity("t1",Map.of("entity","exams","record",r)).getStatusCode());
    var captured=org.mockito.ArgumentCaptor.forClass(Map.class);verify(storage).createRecord(eq("exams"),captured.capture());
    assertEquals("2026-10-09T16:00:00.123Z",captured.getValue().get("startTime"));assertEquals("2026-10-10T00:00:00.123456789+08:00",r.get("startTime"));}
}
