//
// Copyright 2010 Cinch Logic Pty Ltd.
//
// http://www.chililog.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.chililog.server.ui;

import static org.jboss.netty.handler.codec.http.HttpHeaders.*;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.*;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.*;
import static org.jboss.netty.handler.codec.http.HttpVersion.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelFutureProgressListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.FileRegion;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.stream.ChunkedFile;
import org.jboss.netty.util.CharsetUtil;

import com.chililog.server.common.AppProperties;
import com.chililog.server.common.Log4JLogger;

/**
 * <p>
 * Static file service serves static files stored on the file system.
 * </p>
 * <p>
 * The root directory under which to search for file is specified by the <code>web.static_files.directory</code> in the
 * <code>app.properties</code> file.
 * </p>
 * <p>
 * The number of seconds that browsers are expected to cache all files is specified by the
 * <code>web.static_files.cache_seconds</code> in the <code>app.properties</code> file.
 * </p>
 * <p>
 * Compression is turned off for all files except those that:
 * <ul>
 * <li>have an extension of ".html", ".txt", ".json", ".js", ".xml" or ".css", and</li>
 * <li>are between 4K and 1MB in size.</li>
 * </ul>
 * File extension restrictions are put in as these types of text files are the most common and compress well. We don't
 * want to compress files that are too small because the result can be bigger than the original. Also, we don't want to
 * compress files that are too big because it can waste CPU.
 * </p>
 * <p>
 * This code is based on the Netty HTTP File Server sample (http://www.jboss.org/netty/documentation.html).
 * </p>
 */
public class StaticFileService extends Service
{
    private static Log4JLogger _logger = Log4JLogger.getLogger(StaticFileService.class);
    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

    /**
     * Process the message
     */
    @Override
    public void processMessage(ChannelHandlerContext ctx, MessageEvent e) throws Exception
    {
        HttpRequest request = (HttpRequest) e.getMessage();

        // We don't handle 100 Continue because we only allow GET method.
        if (request.getMethod() != HttpMethod.GET)
        {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        // Check
        final String filePath = convertUriToPhysicalFilePath(request.getUri());
        if (filePath == null)
        {
            sendError(ctx, FORBIDDEN);
            return;
        }
        File file = new File(filePath);
        if (file.isHidden() || !file.exists())
        {
            sendError(ctx, NOT_FOUND);
            return;
        }
        if (!file.isFile())
        {
            sendError(ctx, FORBIDDEN);
            return;
        }

        // Cache Validation
        String ifModifiedSince = request.getHeader(HttpHeaders.Names.IF_MODIFIED_SINCE);
        if (!StringUtils.isBlank(ifModifiedSince))
        {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
            Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
            if (ifModifiedSinceDate.getTime() == file.lastModified())
            {
                sendNotModified(ctx);
                return;
            }
        }

        // Open file for sending back
        RandomAccessFile raf;
        try
        {
            raf = new RandomAccessFile(file, "r");
        }
        catch (FileNotFoundException fnfe)
        {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        // Turn compression on/off
        boolean doCompression = checkDoCompression(filePath, fileLength);
        toogleCompression(ctx, doCompression);

        _logger.debug("Getting URI:%s  FILE:%s", request.getUri(), filePath);

        // Create the response
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);

        // Write the content.
        Channel ch = e.getChannel();
        ChannelFuture writeFuture;
        if (doCompression)
        {
            // Cannot use ChunkedFile or zero-copy if we want to do compression
            // Must read file contents and set it as the contents
            byte[] buffer = new byte[(int) fileLength];
            raf.readFully(buffer);
            raf.close();

            response.setContent(ChannelBuffers.copiedBuffer(buffer));
            writeFuture = ch.write(response);
        }
        else if (AppProperties.getInstance().getWebSslEnabled())
        {
            // Cannot use zero-copy with HTTPS

            // Write the initial line and the header.
            ch.write(response);

            // Write chunks
            writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
        }
        else
        {
            // No encryption - use zero-copy.
            // However zero-copy does not seem to work with compression
            // Only use zero-copy for large files like movies and music

            // Write the initial line and the header.
            ch.write(response);

            // Zero-copy
            final FileRegion region = new DefaultFileRegion(raf.getChannel(), 0, fileLength);
            writeFuture = ch.write(region);
            writeFuture.addListener(new ChannelFutureProgressListener()
            {
                public void operationComplete(ChannelFuture future)
                {
                    region.releaseExternalResources();
                }

                public void operationProgressed(ChannelFuture future, long amount, long current, long total)
                {
                    _logger.debug("Zero-Coping file %s: %d / %d (+%d) bytes", filePath, current, total, amount);
                }
            });
        }

        // Decide whether to close the connection or not.
        if (!isKeepAlive(request))
        {
            // Close the connection when the whole content is written out.
            writeFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * Converts the request URI to a file path
     * 
     * @param uri
     * @return
     * @throws UnsupportedEncodingException
     */
    private String convertUriToPhysicalFilePath(String uri) throws UnsupportedEncodingException
    {
        // Decode the path.
        try
        {
            uri = URLDecoder.decode(uri, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            uri = URLDecoder.decode(uri, "ISO-8859-1");
        }

        // Remove /static prefix
        uri = uri.substring(7);

        if (StringUtils.isBlank(uri))
        {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + ".") || uri.contains("." + File.separator) || uri.startsWith(".")
                || uri.endsWith("."))
        {
            return null;
        }

        // Convert to absolute path.
        return AppProperties.getInstance().getWebStaticFilesDirectory() + uri;
    }

    /**
     * Send error to client
     * 
     * @param ctx
     *            Context
     * @param status
     *            HTTP response status
     */
    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status)
    {
        toogleCompression(ctx, false);

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);
        setDateHeader(response);

        // Send error back as plain text in the body
        response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.setContent(ChannelBuffers.copiedBuffer("Failure: " + status.toString() + "\r\n", CharsetUtil.UTF_8));

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * If file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     * 
     * @param ctx
     *            Context
     */
    private void sendNotModified(ChannelHandlerContext ctx)
    {
        toogleCompression(ctx, false);

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * <p>
     * Figure out if we should do try to do HTTP compression or not based on the file extension and file size
     * </p>
     * 
     * @param filePath
     *            Path to the file
     * @return true if compression on the file should be performed, false if not
     */
    private boolean checkDoCompression(String filePath, long fileLength)
    {
        // If < 4096 bytes, compression makes the file bigger and/or CPU used vs time saved is small
        // If > 1 MB, don't compress. Just chunk download because it takes too much memory to read everything in
        if (fileLength < 4096 || fileLength > 1048576)
        {
            return false;
        }

        String s = filePath.toLowerCase();
        if (s.endsWith(".html") || s.endsWith(".htm") || s.endsWith(".js") || s.endsWith(".css") || s.endsWith(".txt")
                || s.endsWith(".json") || s.endsWith(".xml"))
        {
            return true;
        }
        return false;
    }

    /**
     * Sets the content type header on an HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private void setContentTypeHeader(HttpResponse response, File file)
    {
        response.setHeader(HttpHeaders.Names.CONTENT_TYPE, convertFileExtensionToMimeType(file.getPath()));
    }

    /**
     * Tries to figure out the MIME type of a file based on the file name
     * 
     * @param filePath
     *            Path to file
     * @return MIME type. e.g. "text/html"
     */
    private String convertFileExtensionToMimeType(String filePath)
    {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        return mimeTypesMap.getContentType(filePath);
    }

    /**
     * Sets the Date header for the HTTP response
     * 
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private void setDateHeader(HttpResponse response)
    {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     * 
     * @param response
     *            HTTP response
     * @param file
     *            file to extract content type
     */
    private void setDateAndCacheHeaders(HttpResponse response, File filetoCache)
    {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.setHeader(HttpHeaders.Names.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, AppProperties.getInstance().getWebStaticFilesCacheSeconds());
        response.setHeader(HttpHeaders.Names.EXPIRES, dateFormatter.format(time.getTime()));

        response.setHeader(HttpHeaders.Names.CACHE_CONTROL, "private, max-age="
                + AppProperties.getInstance().getWebStaticFilesCacheSeconds());

        response.setHeader(HttpHeaders.Names.LAST_MODIFIED, dateFormatter.format(new Date(filetoCache.lastModified())));
    }

    /**
     * Turn on/off compression
     * 
     * @param ctx
     *            contenxt
     * @param doCompression
     *            True to turn compression on, False to turn it off
     */
    private void toogleCompression(ChannelHandlerContext ctx, boolean doCompression)
    {
        ChannelHandler deflater = ctx.getPipeline().get("deflater");
        if (deflater instanceof ConditionalHttpContentCompressor)
        {
            ((ConditionalHttpContentCompressor) deflater).setDoCompression(doCompression);
        }
    }
}
