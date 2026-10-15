package com.tfzhang.linuxback.service;

public record MarkdownOwner(String ownerType, long ownerId, String... markdown) {
}
