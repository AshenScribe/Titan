//------------------------------------------------------------------------------
// MIT License
//
// Copyright (c) 2026 Rishabh Saraswat
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
//------------------------------------------------------------------------------

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class BaseServiceContainer<SELF extends BaseServiceContainer<SELF>>
        extends GenericContainer<SELF> {

    private final String serviceName;
    private OutputStream outputStream;

    public BaseServiceContainer( DockerImageName imageName, String serviceName ) {
        super(imageName);
        this.serviceName = serviceName;
    }

    @Override
    protected void doStart() {
        withLogConsumer(this::streamToFile);
        super.doStart();
    }

    private void streamToFile( OutputFrame outputFrame ) {
        if (outputStream == null) {
            try {
                Path logDir = Paths.get("build", "output", "container");
                Files.createDirectories(logDir);
                String shortId = getContainerId() != null ? getContainerId().substring(0, 12) : "init";
                File file = logDir.resolve(serviceName + "-" + shortId + ".log").toFile();
                this.outputStream = new FileOutputStream(file, true);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        try {
            outputStream.write(outputFrame.getBytes());
            outputStream.flush();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}

class hello {
    Queue<Integer> queue = new ArrayDeque<>();
    ReentrantLock lock
    public void put(int item) {

    }
}
