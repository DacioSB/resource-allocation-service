package cloud.fogbow.ras.requests.api.local.http;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.ras.api.http.CommonKeys;
import cloud.fogbow.ras.api.http.Image;
import cloud.fogbow.ras.core.ApplicationFacade;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(SpringRunner.class)
@WebMvcTest(value = Image.class, secure = false)
@PrepareForTest(ApplicationFacade.class)
public class ImageTest {

    private final String IMAGE_ENDPOINT = "/" + Image.IMAGE_ENDPOINT;

    @Autowired
    private MockMvc mockMvc;

    private ApplicationFacade facade;

    @Before
    public void setUp() throws FogbowException {
        this.facade = Mockito.spy(ApplicationFacade.class);
        PowerMockito.mockStatic(ApplicationFacade.class);
        BDDMockito.given(ApplicationFacade.getInstance()).willReturn(this.facade);
    }

    // test case: Test getAllImages() when no images were found, it must return an empty JSON.
    @Test
    public void testGetAllImagesWhenHasNoData() throws Exception {
        // set up
        Map<String, String> imagesMap = new HashMap<>();
        Mockito.doReturn(imagesMap).when(this.facade).getAllImages(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        RequestBuilder requestBuilder = createRequestBuilder(IMAGE_ENDPOINT + "/provider/cloud", getHttpHeaders(), "");

        // exercise
        MvcResult result = this.mockMvc.perform(requestBuilder).andReturn();

        // verify
        int expectedStatus = HttpStatus.OK.value();
        Assert.assertEquals("{}", result.getResponse().getContentAsString());
        Assert.assertEquals(expectedStatus, result.getResponse().getStatus());
    }

    // test case: Test getAllImages() when there are some images.
    @Test
    public void testGetAllImagesWhenHasData() throws Exception {
        // set up
        Map<String, String> imagesMap = new HashMap<>();
        imagesMap.put("image-id1", "image-name1");
        imagesMap.put("image-id2", "image-name2");
        imagesMap.put("image-id3", "image-name3");

        Mockito.doReturn(imagesMap).when(this.facade).getAllImages(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        RequestBuilder requestBuilder = createRequestBuilder(IMAGE_ENDPOINT + "/provider/cloud", getHttpHeaders(), "");

        // exercise
        MvcResult result = this.mockMvc.perform(requestBuilder).andReturn();

        // verify
        int expectedStatus = HttpStatus.OK.value();
        Assert.assertEquals(expectedStatus, result.getResponse().getStatus());

        TypeToken<Map<String, String>> token = new TypeToken<Map<String, String>>() {
        };
        Map<String, String> resultMap = new Gson().fromJson(result.getResponse().getContentAsString(), token.getType());
        Assert.assertEquals(3, resultMap.size());
    }

    // test case: Test if given an existing image id, the getImageId() returns that image properly.
    @Test
    public void testGetImageById() throws Exception {
        // set up
        String fakeId = "fake-Id-1";
        String imageEndpoint = IMAGE_ENDPOINT + "/provider/cloud/" + fakeId;

        cloud.fogbow.ras.core.models.images.Image image = new cloud.fogbow.ras.core.models.images.Image(fakeId, "fake-name", 1, 1, 1, "READY");

        Mockito.doReturn(image).when(this.facade).getImage(Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString());

        RequestBuilder requestBuilder = createRequestBuilder(imageEndpoint, getHttpHeaders(), "");

        // exercise
        MvcResult result = this.mockMvc.perform(requestBuilder).andReturn();

        // verify
        int expectedStatus = HttpStatus.OK.value();
        Assert.assertEquals(expectedStatus, result.getResponse().getStatus());

        cloud.fogbow.ras.core.models.images.Image resultImage = new Gson().fromJson(result.getResponse().getContentAsString(), cloud.fogbow.ras.core.models.images.Image.class);
        Assert.assertEquals(image.getId(), resultImage.getId());
        Assert.assertEquals(image.getName(), resultImage.getName());
        Assert.assertEquals(image.getStatus(), resultImage.getStatus());
        Assert.assertEquals(image.getMinDisk(), resultImage.getMinDisk());
        Assert.assertEquals(image.getMinRam(), resultImage.getMinRam());
        Assert.assertEquals(image.getSize(), resultImage.getSize());
    }

    // test case: Test if given an invalid image id, the getImageId() returns just NOT_FOUND.
    @Test
    public void testGetImageWithInvalidId() throws Exception {
        // set up
        String fakeId = "fake-Id-1";
        String imageEndpoint = IMAGE_ENDPOINT + "/" + fakeId;

        Mockito.doThrow(new InstanceNotFoundException()).when(this.facade).getImage(Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        RequestBuilder requestBuilder = createRequestBuilder(imageEndpoint, getHttpHeaders(), "");

        // exercise
        MvcResult result = this.mockMvc.perform(requestBuilder).andReturn();

        // verify
        int expectedStatus = HttpStatus.NOT_FOUND.value();
        Assert.assertEquals(expectedStatus, result.getResponse().getStatus());
    }

    private RequestBuilder createRequestBuilder(String urlTemplate, HttpHeaders headers, String body) {
        return MockMvcRequestBuilders.get(urlTemplate)
                .headers(headers)
                .accept(MediaType.APPLICATION_JSON)
                .content(body)
                .contentType(MediaType.APPLICATION_JSON);
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String fakeFederationTokenValue = "fake-access-id";
        headers.set(CommonKeys.FEDERATION_TOKEN_VALUE_HEADER_KEY, fakeFederationTokenValue);
        return headers;
    }
}
