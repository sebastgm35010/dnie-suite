#pragma once
#include <windows.h>
#include <winioctl.h>
#include <cstdint>

// Original ABI: all integer fields are little endian. No network endpoint.
constexpr ULONG BRIDGE_VERSION = 1;
constexpr ULONG BRIDGE_MAX_APDU = 65535;
constexpr ULONG BRIDGE_ATTACH = CTL_CODE(FILE_DEVICE_UNKNOWN, 0x800, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA);
constexpr ULONG BRIDGE_DETACH = CTL_CODE(FILE_DEVICE_UNKNOWN, 0x801, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA);
constexpr ULONG BRIDGE_GET_REQUEST = CTL_CODE(FILE_DEVICE_UNKNOWN, 0x802, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA);
constexpr ULONG BRIDGE_COMPLETE = CTL_CODE(FILE_DEVICE_UNKNOWN, 0x803, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA);
constexpr ULONG BRIDGE_REPUBLISH = CTL_CODE(FILE_DEVICE_UNKNOWN, 0x804, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA);
constexpr ULONG OP_TRANSMIT = 1;
constexpr ULONG OP_RESET = 2;
constexpr ULONG OP_POWER_OFF = 3;
#pragma pack(push, 1)
struct AttachHeader { uint32_t version; uint32_t protocols; uint32_t atrLength; uint32_t reserved; };
struct RequestHeader { uint64_t id; uint32_t opcode; uint32_t inputLength; uint32_t outputCapacity; uint32_t reserved; };
struct CompleteHeader { uint64_t id; uint32_t status; uint32_t length; };
#pragma pack(pop)
static_assert(sizeof(AttachHeader) == 16);
static_assert(sizeof(RequestHeader) == 24);
static_assert(sizeof(CompleteHeader) == 16);
