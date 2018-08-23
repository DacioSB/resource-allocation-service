package org.fogbowcloud.manager.api.http;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.ApplicationFacade;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = VersionRequestController.VERSION_ENDPOINT)
public class VersionRequestController {
    public static final String VERSION_ENDPOINT = "version";

    private final Logger LOGGER = Logger.getLogger(VersionRequestController.class);

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> getVersion() {
        LOGGER.info("New version request received.");

        String versionNumber = ApplicationFacade.getInstance().getVersionNumber();
        return new ResponseEntity<>(versionNumber, HttpStatus.OK);
    }
}
