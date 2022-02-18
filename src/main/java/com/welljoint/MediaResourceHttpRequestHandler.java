package com.welljoint;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;

/**
 * Created on 2020/5/14
 *
 * @author cyjjohn
 */
@Component
public class MediaResourceHttpRequestHandler extends ResourceHttpRequestHandler {
    public final static String ATTR_FILE = MediaResourceHttpRequestHandler.class.getName() + ".file";

    @Override
    protected Resource getResource(HttpServletRequest request) throws IOException {

        final File file = (File) request.getAttribute(ATTR_FILE);
        return new FileSystemResource(file);
    }
}
