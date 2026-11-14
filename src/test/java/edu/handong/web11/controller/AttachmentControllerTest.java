package edu.handong.web11.controller;

import edu.handong.web11.domain.Attachment;
import edu.handong.web11.repository.AttachmentRepository;
import edu.handong.web11.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AttachmentControllerTest {
    @TempDir Path directory;
    AttachmentRepository repository;
    MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(AttachmentRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new AttachmentController(repository,
            new FileStorageService(directory.toString()))).build();
    }

    @ParameterizedTest
    @CsvSource({"jpg,image/jpeg", "jpeg,image/jpeg", "png,image/png", "gif,image/gif"})
    void supportedImagesAreReturnedInline(String extension, String type) throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        Files.write(directory.resolve("stored." + extension), bytes);
        when(repository.findById(1L)).thenReturn(Optional.of(
            new Attachment("이미지." + extension, "stored." + extension, type, bytes.length)));
        mvc.perform(get("/attachments/1/view"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(type))
            .andExpect(content().bytes(bytes))
            .andExpect(header().string("Content-Disposition", containsString("inline")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void pdfIsDownloadableButCannotBePreviewed() throws Exception {
        byte[] bytes = "%PDF-1.4 test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(directory.resolve("stored.pdf"), bytes);
        when(repository.findById(1L)).thenReturn(Optional.of(
            new Attachment("자료.pdf", "stored.pdf", "application/pdf", bytes.length)));
        mvc.perform(get("/attachments/1/view")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/attachments/1/download"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(bytes))
            .andExpect(header().string("Content-Disposition", containsString("attachment")));
    }

    @Test
    void unknownAttachmentReturns404() throws Exception {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/attachments/99/view")).andExpect(status().isNotFound());
        mvc.perform(get("/attachments/99/download")).andExpect(status().isNotFound());
    }

    @Test
    void missingDiskFileReturns404() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(
            new Attachment("missing.png", "missing.png", "image/png", 10)));
        mvc.perform(get("/attachments/1/view")).andExpect(status().isNotFound());
        mvc.perform(get("/attachments/1/download")).andExpect(status().isNotFound());
    }

    @Test
    void malformedIdReturns400() throws Exception {
        mvc.perform(get("/attachments/invalid/view")).andExpect(status().isBadRequest());
    }

    @Test
    void mismatchedExtensionAndTypeIsRejected() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(
            new Attachment("document.pdf", "stored.pdf", "image/png", 10)));
        mvc.perform(get("/attachments/1/view")).andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void pathOutsideUploadDirectoryIsRejected() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(
            new Attachment("image.png", "../outside.png", "image/png", 10)));
        mvc.perform(get("/attachments/1/view")).andExpect(status().isNotFound());
    }
}
