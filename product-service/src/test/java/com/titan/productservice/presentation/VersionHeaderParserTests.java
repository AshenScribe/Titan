package com.titan.productservice.util;
import com.titan.productservice.controller.*;
import com.titan.productservice.service.*;
import com.titan.productservice.dto.*;
import com.titan.productservice.exception.*;
import com.titan.productservice.config.*;
import com.titan.productservice.entity.*;
import com.titan.productservice.repository.*;
import com.titan.productservice.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VersionHeaderParserTests {
    @Test
    void parsesQuotedAndUnquotedVersions() {
        assertEquals(42L, VersionHeaderParser.parse("42"));
        assertEquals(42L, VersionHeaderParser.parse(" \"42\" "));
    }

    @Test
    void rejectsMissingAndNonNumericVersions() {
        assertThrows(IllegalArgumentException.class, () -> VersionHeaderParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> VersionHeaderParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> VersionHeaderParser.parse("version"));
    }
}
