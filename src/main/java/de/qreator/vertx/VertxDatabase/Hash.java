/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.qreator.vertx.VertxDatabase;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author j.benecke2
 */
class Hash {
        public static String checksum(InputStream content,
            String hashFunction) throws Exception {

        byte[] buffer = new byte[8192];
        MessageDigest md = MessageDigest.getInstance(hashFunction);

        try (DigestInputStream dis = new DigestInputStream(content, md)) {
            while (dis.read(buffer) != -1);
        }
        return DatatypeConverter.printHexBinary(md.digest());
    }
}
