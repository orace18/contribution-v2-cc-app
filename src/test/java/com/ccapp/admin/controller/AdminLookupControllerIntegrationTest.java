package com.ccapp.admin.controller;

import com.ccapp.admin.dto.LookupItemResponse;
import com.ccapp.admin.service.AdminLookupService;
import com.ccapp.common.api.CommonResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AdminLookupController.class)
class AdminLookupControllerIntegrationTest {

    @Autowired
    private AdminLookupController controller;

    @MockBean
    private AdminLookupService adminLookupService;

    @Test
    void listWorldCountries_shouldReturnActiveWorldCountriesFromLookupService() {
        when(adminLookupService.listWorldCountries()).thenReturn(List.of(
                new LookupItemResponse("GH", "Ghana", "GHS"),
                new LookupItemResponse("SN", "Senegal", "XOF")
        ));

        ResponseEntity<CommonResponse<List<LookupItemResponse>>> response = controller.listWorldCountries();

        assertEquals(2, response.getBody().data().size());
        assertEquals("GH", response.getBody().data().get(0).id());
        assertEquals("Ghana", response.getBody().data().get(0).label());
    }
}
