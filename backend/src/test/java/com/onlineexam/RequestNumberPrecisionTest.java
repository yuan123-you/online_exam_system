package com.onlineexam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineexam.config.GlobalExceptionHandler;
import com.onlineexam.controller.AdminController;
import com.onlineexam.service.*;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RequestNumberPrecisionTest {
  ApplicationContextRunner configured(){
    var runner=new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));
    try {return runner.withUserConfiguration(Class.forName("com.onlineexam.config.RequestNumberConfig"));}
    catch(ClassNotFoundException old){return runner;}
  }
  @ParameterizedTest @ValueSource(strings={"2.0000000000000000000000001","2.5","4294967297"})
  void HTTPQuestionWeightCannotLosePrecisionBeforeValidation(String raw){
    configured().run(ctx->{
      var mapper=ctx.getBean(ObjectMapper.class);var storage=mock(StoreService.class);var view=new StoreService.Store();
      view.users.add(Map.of("id","t1","role","teacher"));when(storage.readStore()).thenReturn(view);
      var auth=mock(AuthService.class);var logs=mock(SystemLogService.class);var service=new EntityCrudService(storage,auth,logs);
      var mvc=MockMvcBuilders.standaloneSetup(new AdminController(storage,service,auth,logs))
          .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).setControllerAdvice(new GlobalExceptionHandler()).build();
      mvc.perform(post("/api/entities").header("X-User-Id","t1").contentType(MediaType.APPLICATION_JSON)
          .content("{\"entity\":\"questions\",\"record\":{\"title\":\"New\",\"subject\":\"Math\",\"type\":\"single\",\"score\":"+raw+"}}"))
          .andExpect(status().isBadRequest());
      verify(storage,never()).createRecord(anyString(),anyMap());
    });
  }
  @ParameterizedTest @ValueSource(strings={"2","2.0","2e0","\"2\""})
  void exactIntegralQuestionWeightsRemainCompatible(String raw){
    configured().run(ctx->{
      var mapper=ctx.getBean(ObjectMapper.class);var storage=mock(StoreService.class);var view=new StoreService.Store();
      view.users.add(Map.of("id","t1","role","teacher"));when(storage.readStore()).thenReturn(view);
      var auth=mock(AuthService.class);var logs=mock(SystemLogService.class);var service=new EntityCrudService(storage,auth,logs);
      var mvc=MockMvcBuilders.standaloneSetup(new AdminController(storage,service,auth,logs))
          .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).setControllerAdvice(new GlobalExceptionHandler()).build();
      mvc.perform(post("/api/entities").header("X-User-Id","t1").contentType(MediaType.APPLICATION_JSON)
          .content("{\"entity\":\"questions\",\"record\":{\"title\":\"New\",\"subject\":\"Math\",\"type\":\"single\",\"score\":"+raw+"}}"))
          .andExpect(status().isOk());
      verify(storage).createRecord(eq("questions"),anyMap());
    });
  }
}
