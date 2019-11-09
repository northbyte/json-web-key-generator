package org.mitre.jose.jwk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.*;
import org.apache.commons.cli.*;
import org.apache.commons.io.IOUtils;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;

/**
 * Small Helper App to generate Json Web Keys
 */
public class Launcher {

    private static Options options;

    public static void main(String[] args) {

        options = new Options();

        options.addOption("t", true, "Key Type, one of: " + KeyType.RSA.getValue() + ", " + KeyType.OCT.getValue() + ", " +
                KeyType.EC.getValue() + ", " + KeyType.OKP.getValue());
        options.addOption("s", true, "Key Size in bits, required for RSA and oct key types. Must be an integer divisible by 8");
        options.addOption("u", true, "Usage, one of: enc, sig (optional)");
        options.addOption("a", true, "Algorithm (optional)");
        options.addOption("i", true, "Key ID (optional), one will be generated if not defined");
        options.addOption("I", false, "Don't generate a Key ID if none defined");
        options.addOption("p", false, "Display public key separately");
        options.addOption("c", true, "Key Curve, required for EC or EdDSA key types. Must be one of " + Curve.P_256 + ", " + Curve.P_384
				+ ", " + Curve.P_521 + " or for EdDSA " + Curve.Ed25519 + ", " + Curve.X25519);
        options.addOption("S", false, "Wrap the generated key in a KeySet");
        options.addOption("o", true, "Write output to file (will append to existing KeySet if -S is used), No Display of Key "
				+ "Material");

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            String kty = cmd.getOptionValue("t");
            String size = cmd.getOptionValue("s");
            String use = cmd.getOptionValue("u");
            String alg = cmd.getOptionValue("a");
            String kid = cmd.getOptionValue("i");
            String crv = cmd.getOptionValue("c");
            boolean keySet = cmd.hasOption("S");
            boolean pubKey = cmd.hasOption("p");
            boolean doNotGenerateKid = cmd.hasOption("I");
            String outFile = cmd.getOptionValue("o");

            // check for required fields
            if (kty == null) {
                printUsageAndExit("Key type must be supplied.");
            }

            // parse out the important bits

            KeyType keyType = KeyType.parse(kty);

            KeyUse keyUse = null;
            if (use != null) {
                if (use.equals("sig")) {
                    keyUse = KeyUse.SIGNATURE;
                } else if (use.equals("enc")) {
                    keyUse = KeyUse.ENCRYPTION;
                } else {
                    printUsageAndExit("Invalid key usage, must be 'sig' or 'enc', got " + use);
                }
            }

            if (Strings.isNullOrEmpty(kid)) {
                kid = doNotGenerateKid ? null : generateKid(keyUse);
            }

            Algorithm keyAlg = null;
            if (!Strings.isNullOrEmpty(alg)) {
                keyAlg = JWSAlgorithm.parse(alg);
            }

            JWK jwk = null;

            if (keyType.equals(KeyType.RSA)) {
                // surrounding try/catch catches numberformatexception from this
                if (Strings.isNullOrEmpty(size)) {
                    printUsageAndExit("Key size (in bits) is required for key type " + keyType);
                }

                Integer keySize = Integer.decode(size);
                if (keySize % 8 != 0) {
                    printUsageAndExit("Key size (in bits) must be divisible by 8, got " + keySize);
                }

                jwk = RSAKeyMaker.make(keySize, keyUse, keyAlg, kid);
            } else if (keyType.equals(KeyType.OCT)) {
                // surrounding try/catch catches numberformatexception from this
                if (Strings.isNullOrEmpty(size)) {
                    printUsageAndExit("Key size (in bits) is required for key type " + keyType);
                }
                Integer keySize = Integer.decode(size);
                if (keySize % 8 != 0) {
                    printUsageAndExit("Key size (in bits) must be divisible by 8, got " + keySize);
                }

                jwk = OctetSequenceKeyMaker.make(keySize, keyUse, keyAlg, kid);
            } else if (keyType.equals(KeyType.EC)) {
                if (Strings.isNullOrEmpty(crv)) {
                    printUsageAndExit("Curve is required for key type " + keyType);
                }
                Curve keyCurve = Curve.parse(crv);
                jwk = ECKeyMaker.make(keyCurve, keyUse, keyAlg, kid);
            } else if (keyType.equals(KeyType.OKP)) {
                if (Strings.isNullOrEmpty(crv)) {
                    printUsageAndExit("Curve is required for key type " + keyType);
                }
                Curve keyCurve = Curve.parse(crv);
                jwk = OctetKeyPairKeyMaker.make(keyCurve, keyUse, kid);
            } else {
                printUsageAndExit("Unknown key type: " + keyType);
            }

            // round trip it through GSON to get a prettyprinter
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            if (outFile == null) {

                System.out.println("Full key:");

                printKey(keySet, jwk, gson);

                if (pubKey) {
                    System.out.println(); // spacer

                    // also print public key, if possible
                    JWK pub = jwk.toPublicJWK();

                    if (pub != null) {
                        System.out.println("Public key:");
                        printKey(keySet, pub, gson);
                    } else {
                        System.out.println("No public key.");
                    }
                }
            } else {
                writeKeyToFile(keySet, outFile, jwk, gson);
            }

        } catch (NumberFormatException e) {
            printUsageAndExit("Invalid key size: " + e.getMessage());
        } catch (ParseException e) {
            printUsageAndExit("Failed to parse arguments: " + e.getMessage());
        } catch (java.text.ParseException e) {
            printUsageAndExit("Could not parse existing KeySet: " + e.getMessage());
        } catch (IOException e) {
            printUsageAndExit("Could not read existing KeySet: " + e.getMessage());
        }
    }

    private static String generateKid(KeyUse keyUse) {
        String prefix = keyUse == null ? "" : keyUse.identifier();
        return prefix + UUID.randomUUID().toString();
    }

    private static void writeKeyToFile(boolean keySet, String outFile, JWK jwk, Gson gson) throws IOException,
            java.text.ParseException {
        JsonElement json;
        File output = new File(outFile);
        if (keySet) {
            List<JWK> existingKeys = output.exists() ? JWKSet.load(output).getKeys() : Collections.emptyList();
            List<JWK> jwkList = new ArrayList<>(existingKeys);
            jwkList.add(jwk);
            JWKSet jwkSet = new JWKSet(jwkList);
            json = new JsonParser().parse(jwkSet.toJSONObject(false).toJSONString());
        } else {
            json = new JsonParser().parse(jwk.toJSONString());
        }

        try (OutputStream os = new FileOutputStream(output)) {
            IOUtils.write(gson.toJson(json), os, Charset.defaultCharset());
        } catch (Exception ignored) {}
    }

    private static void printKey(boolean keySet, JWK jwk, Gson gson) {
        if (keySet) {
            JWKSet jwkSet = new JWKSet(jwk);
            JsonElement json = new JsonParser().parse(jwkSet.toJSONObject(false).toJSONString());
            System.out.println(gson.toJson(json));
        } else {
            JsonElement json = new JsonParser().parse(jwk.toJSONString());
            System.out.println(gson.toJson(json));
        }
    }

    // print out a usage message and quit
    private static void printUsageAndExit(String message) {
        if (message != null) {
            System.err.println(message);
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar json-web-key-generator.jar -t <keyType> [options]", options);

        // kill the program
        System.exit(1);
    }
}
