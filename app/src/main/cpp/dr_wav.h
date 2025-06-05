/*
WAV audio loader and writer. Choice of public domain or MIT-0. See license state
ments at the end of this file.
dr_wav - v0.14.0 - TBD

David Reid - mackron@gmail.com

GitHub: https://github.com/mackron/dr_libs
*/

/*
Introduction
============
This is a single file library. To use it, do something like the following in one
 .c file.

    ```c
    #define DR_WAV_IMPLEMENTATION
    #include "dr_wav.h"
    ```

You can then #include this file in other parts of the program as you would with
any other header file. Do something like the following to read audio data:

    ```c
    drwav wav;
    if (!drwav_init_file(&wav, "my_song.wav", NULL)) {
        // Error opening WAV file.
    }

    drwav_int32* pDecodedInterleavedPCMFrames = malloc(wav.totalPCMFrameCount *
wav.channels * sizeof(drwav_int32));
    size_t numberOfSamplesActuallyDecoded = drwav_read_pcm_frames_s32(&wav, wav.
totalPCMFrameCount, pDecodedInterleavedPCMFrames);

    ...

    drwav_uninit(&wav);
    ```

If you just want to quickly open and read the audio data in a single operation y
ou can do something like this:

    ```c
    unsigned int channels;
    unsigned int sampleRate;
    drwav_uint64 totalPCMFrameCount;
    float* pSampleData = drwav_open_file_and_read_pcm_frames_f32("my_song.wav",
&channels, &sampleRate, &totalPCMFrameCount, NULL);
    if (pSampleData == NULL) {
        // Error opening and reading WAV file.
    }

    ...

    drwav_free(pSampleData, NULL);
    ```

The examples above use versions of the API that convert the audio data to a cons
istent format (32-bit signed PCM, in this case), but you can still output the
audio data in its internal format (see notes below for supported formats):

    ```c
    size_t framesRead = drwav_read_pcm_frames(&wav, wav.totalPCMFrameCount, pDec
odedInterleavedPCMFrames);
    ```

You can also read the raw bytes of audio data, which could be useful if dr_wav d
oes not have native support for a particular data format:

    ```c
    size_t bytesRead = drwav_read_raw(&wav, bytesToRead, pRawDataBuffer);
    ```

dr_wav can also be used to output WAV files. This does not currently support com
pressed formats. To use this, look at `drwav_init_write()`,
`drwav_init_file_write()`, etc. Use `drwav_write_pcm_frames()` to write samples,
 or `drwav_write_raw()` to write raw data in the "data" chunk.

    ```c
    drwav_data_format format;
    format.container = drwav_container_riff;     // <-- drwav_container_riff = n
ormal WAV files, drwav_container_w64 = Sony Wave64.
    format.format = DR_WAVE_FORMAT_PCM;          // <-- Any of the DR_WAVE_FORMA
T_* codes.
    format.channels = 2;
    format.sampleRate = 44100;
    format.bitsPerSample = 16;
    drwav_init_file_write(&wav, "data/recording.wav", &format, NULL);

    ...

    drwav_uint64 framesWritten = drwav_write_pcm_frames(pWav, frameCount, pSampl
es);
    ```

Note that writing to AIFF or RIFX is not supported.

dr_wav has support for decoding from a number of different encapsulation formats
. See below for details.


Build Options
=============
#define these options before including this file.

#define DR_WAV_NO_CONVERSION_API
  Disables conversion APIs such as `drwav_read_pcm_frames_f32()` and `drwav_s16_
to_f32()`.

#define DR_WAV_NO_STDIO
  Disables APIs that initialize a decoder from a file such as `drwav_init_file()
`, `drwav_init_file_write()`, etc.

#define DR_WAV_NO_WCHAR
  Disables all functions ending with `_w`. Use this if your compiler does not pr
ovide wchar.h. Not required if DR_WAV_NO_STDIO is also defined.


Supported Encapsulations
========================
- RIFF (Regular WAV)
- RIFX (Big-Endian)
- AIFF (Does not currently support ADPCM)
- RF64
- W64

Note that AIFF and RIFX do not support write mode, nor do they support reading o
f metadata.


Supported Encodings
===================
- Unsigned 8-bit PCM
- Signed 12-bit PCM
- Signed 16-bit PCM
- Signed 24-bit PCM
- Signed 32-bit PCM
- IEEE 32-bit floating point
- IEEE 64-bit floating point
- A-law and u-law
- Microsoft ADPCM
- IMA ADPCM (DVI, format code 0x11)

8-bit PCM encodings are always assumed to be unsigned. Signed 8-bit encoding can
 only be read with `drwav_read_raw()`.

Note that ADPCM is not currently supported with AIFF. Contributions welcome.


Notes
=====
- Samples are always interleaved.
- The default read function does not do any data conversion. Use `drwav_read_pcm
_frames_f32()`, `drwav_read_pcm_frames_s32()` and `drwav_read_pcm_frames_s16()`
  to read and convert audio data to 32-bit floating point, signed 32-bit integer
 and signed 16-bit integer samples respectively.
- dr_wav will try to read the WAV file as best it can, even if it's not strictly
 conformant to the WAV format.
*/

#ifndef dr_wav_h
#define dr_wav_h

#ifdef __cplusplus
extern "C" {
#endif

#define DRWAV_STRINGIFY(x)      #x
#define DRWAV_XSTRINGIFY(x)     DRWAV_STRINGIFY(x)

#define DRWAV_VERSION_MAJOR     0
#define DRWAV_VERSION_MINOR     14
#define DRWAV_VERSION_REVISION  0
#define DRWAV_VERSION_STRING    DRWAV_XSTRINGIFY(DRWAV_VERSION_MAJOR) "." DRWAV_
XSTRINGIFY(DRWAV_VERSION_MINOR) "." DRWAV_XSTRINGIFY(DRWAV_VERSION_REVISION)

#include <stddef.h> /* For size_t. */

/* Sized Types */
typedef   signed char           drwav_int8;
typedef unsigned char           drwav_uint8;
typedef   signed short          drwav_int16;
typedef unsigned short          drwav_uint16;
typedef   signed int            drwav_int32;
typedef unsigned int            drwav_uint32;
#if defined(_MSC_VER) && !defined(__clang__)
    typedef   signed __int64    drwav_int64;
    typedef unsigned __int64    drwav_uint64;
#else
    #if defined(__clang__) || (defined(__GNUC__) && (__GNUC__ > 4 || (__GNUC__ =
= 4 && __GNUC_MINOR__ >= 6)))
        #pragma GCC diagnostic push
        #pragma GCC diagnostic ignored "-Wlong-long"
        #if defined(__clang__)
            #pragma GCC diagnostic ignored "-Wc++11-long-long"
        #endif
    #endif
    typedef   signed long long  drwav_int64;
    typedef unsigned long long  drwav_uint64;
    #if defined(__clang__) || (defined(__GNUC__) && (__GNUC__ > 4 || (__GNUC__ =
= 4 && __GNUC_MINOR__ >= 6)))
        #pragma GCC diagnostic pop
    #endif
#endif
#if defined(__LP64__) || defined(_WIN64) || (defined(__x86_64__) && !defined(__I
LP32__)) || defined(_M_X64) || defined(__ia64) || defined (_M_IA64) || defined(_
_aarch64__) || defined(_M_ARM64) || defined(_M_ARM64EC) || defined(__powerpc64__
)
    typedef drwav_uint64        drwav_uintptr;
#else
    typedef drwav_uint32        drwav_uintptr;
#endif
typedef drwav_uint8             drwav_bool8;
typedef drwav_uint32            drwav_bool32;
#define DRWAV_TRUE              1
#define DRWAV_FALSE             0
/* End Sized Types */

/* Decorations */
#if !defined(DRWAV_API)
    #if defined(DRWAV_DLL)
        #if defined(_WIN32)
            #define DRWAV_DLL_IMPORT  __declspec(dllimport)
            #define DRWAV_DLL_EXPORT  __declspec(dllexport)
            #define DRWAV_DLL_PRIVATE static
        #else
            #if defined(__GNUC__) && __GNUC__ >= 4
                #define DRWAV_DLL_IMPORT  __attribute__((visibility("default")))
                #define DRWAV_DLL_EXPORT  __attribute__((visibility("default")))
                #define DRWAV_DLL_PRIVATE __attribute__((visibility("hidden")))
            #else
                #define DRWAV_DLL_IMPORT
                #define DRWAV_DLL_EXPORT
                #define DRWAV_DLL_PRIVATE static
            #endif
        #endif

        #if defined(DR_WAV_IMPLEMENTATION) || defined(DRWAV_IMPLEMENTATION)
            #define DRWAV_API  DRWAV_DLL_EXPORT
        #else
            #define DRWAV_API  DRWAV_DLL_IMPORT
        #endif
        #define DRWAV_PRIVATE DRWAV_DLL_PRIVATE
    #else
        #define DRWAV_API extern
        #define DRWAV_PRIVATE static
    #endif
#endif
/* End Decorations */

/* Result Codes */
typedef drwav_int32 drwav_result;
#define DRWAV_SUCCESS                        0
#define DRWAV_ERROR                         -1   /* A generic error. */
#define DRWAV_INVALID_ARGS                  -2
#define DRWAV_INVALID_OPERATION             -3
#define DRWAV_OUT_OF_MEMORY                 -4
#define DRWAV_OUT_OF_RANGE                  -5
#define DRWAV_ACCESS_DENIED                 -6
#define DRWAV_DOES_NOT_EXIST                -7
#define DRWAV_ALREADY_EXISTS                -8
#define DRWAV_TOO_MANY_OPEN_FILES           -9
#define DRWAV_INVALID_FILE                  -10
#define DRWAV_TOO_BIG                       -11
#define DRWAV_PATH_TOO_LONG                 -12
#define DRWAV_NAME_TOO_LONG                 -13
#define DRWAV_NOT_DIRECTORY                 -14
#define DRWAV_IS_DIRECTORY                  -15
#define DRWAV_DIRECTORY_NOT_EMPTY           -16
#define DRWAV_END_OF_FILE                   -17
#define DRWAV_NO_SPACE                      -18
#define DRWAV_BUSY                          -19
#define DRWAV_IO_ERROR                      -20
#define DRWAV_INTERRUPT                     -21
#define DRWAV_UNAVAILABLE                   -22
#define DRWAV_ALREADY_IN_USE                -23
#define DRWAV_BAD_ADDRESS                   -24
#define DRWAV_BAD_SEEK                      -25
#define DRWAV_BAD_PIPE                      -26
#define DRWAV_DEADLOCK                      -27
#define DRWAV_TOO_MANY_LINKS                -28
#define DRWAV_NOT_IMPLEMENTED               -29
#define DRWAV_NO_MESSAGE                    -30
#define DRWAV_BAD_MESSAGE                   -31
#define DRWAV_NO_DATA_AVAILABLE             -32
#define DRWAV_INVALID_DATA                  -33
#define DRWAV_TIMEOUT                       -34
#define DRWAV_NO_NETWORK                    -35
#define DRWAV_NOT_UNIQUE                    -36
#define DRWAV_NOT_SOCKET                    -37
#define DRWAV_NO_ADDRESS                    -38
#define DRWAV_BAD_PROTOCOL                  -39
#define DRWAV_PROTOCOL_UNAVAILABLE          -40
#define DRWAV_PROTOCOL_NOT_SUPPORTED        -41
#define DRWAV_PROTOCOL_FAMILY_NOT_SUPPORTED -42
#define DRWAV_ADDRESS_FAMILY_NOT_SUPPORTED  -43
#define DRWAV_SOCKET_NOT_SUPPORTED          -44
#define DRWAV_CONNECTION_RESET              -45
#define DRWAV_ALREADY_CONNECTED             -46
#define DRWAV_NOT_CONNECTED                 -47
#define DRWAV_CONNECTION_REFUSED            -48
#define DRWAV_NO_HOST                       -49
#define DRWAV_IN_PROGRESS                   -50
#define DRWAV_CANCELLED                     -51
#define DRWAV_MEMORY_ALREADY_MAPPED         -52
#define DRWAV_AT_END                        -53
/* End Result Codes */

/* Common data formats. */
#define DR_WAVE_FORMAT_PCM          0x1
#define DR_WAVE_FORMAT_ADPCM        0x2
#define DR_WAVE_FORMAT_IEEE_FLOAT   0x3
#define DR_WAVE_FORMAT_ALAW         0x6
#define DR_WAVE_FORMAT_MULAW        0x7
#define DR_WAVE_FORMAT_DVI_ADPCM    0x11
#define DR_WAVE_FORMAT_EXTENSIBLE   0xFFFE

/* Flags to pass into drwav_init_ex(), etc. */
#define DRWAV_SEQUENTIAL            0x00000001
#define DRWAV_WITH_METADATA         0x00000002

DRWAV_API void drwav_version(drwav_uint32* pMajor, drwav_uint32* pMinor, drwav_u
int32* pRevision);
DRWAV_API const char* drwav_version_string(void);

/* Allocation Callbacks */
typedef struct
{
    void* pUserData;
    void* (* onMalloc)(size_t sz, void* pUserData);
    void* (* onRealloc)(void* p, size_t sz, void* pUserData);
    void  (* onFree)(void* p, void* pUserData);
} drwav_allocation_callbacks;
/* End Allocation Callbacks */

typedef enum
{
    drwav_seek_origin_start,
    drwav_seek_origin_current
} drwav_seek_origin;

typedef enum
{
    drwav_container_riff,
    drwav_container_rifx,
    drwav_container_w64,
    drwav_container_rf64,
    drwav_container_aiff
} drwav_container;

typedef struct
{
    union
    {
        drwav_uint8 fourcc[4];
        drwav_uint8 guid[16];
    } id;

    /* The size in bytes of the chunk. */
    drwav_uint64 sizeInBytes;

    /*
    RIFF = 2 byte alignment.
    W64  = 8 byte alignment.
    */
    unsigned int paddingSize;
} drwav_chunk_header;

typedef struct
{
    /*
    The format tag exactly as specified in the wave file's "fmt" chunk. This can
 be used by applications
    that require support for data formats not natively supported by dr_wav.
    */
    drwav_uint16 formatTag;

    /* The number of channels making up the audio data. When this is set to 1 it
 is mono, 2 is stereo, etc. */
    drwav_uint16 channels;

    /* The sample rate. Usually set to something like 44100. */
    drwav_uint32 sampleRate;

    /* Average bytes per second. You probably don't need this, but it's left her
e for informational purposes. */
    drwav_uint32 avgBytesPerSec;

    /* Block align. This is equal to the number of channels * bytes per sample.
*/
    drwav_uint16 blockAlign;

    /* Bits per sample. */
    drwav_uint16 bitsPerSample;

    /* The size of the extended data. Only used internally for validation, but l
eft here for informational purposes. */
    drwav_uint16 extendedSize;

    /*
    The number of valid bits per sample. When <formatTag> is equal to WAVE_FORMA
T_EXTENSIBLE, <bitsPerSample>
    is always rounded up to the nearest multiple of 8. This variable contains in
formation about exactly how
    many bits are valid per sample. Mainly used for informational purposes.
    */
    drwav_uint16 validBitsPerSample;

    /* The channel mask. Not used at the moment. */
    drwav_uint32 channelMask;

    /* The sub-format, exactly as specified by the wave file. */
    drwav_uint8 subFormat[16];
} drwav_fmt;

DRWAV_API drwav_uint16 drwav_fmt_get_format(const drwav_fmt* pFMT);


/*
Callback for when data is read. Return value is the number of bytes actually rea
d.

pUserData   [in]  The user data that was passed to drwav_init() and family.
pBufferOut  [out] The output buffer.
bytesToRead [in]  The number of bytes to read.

Returns the number of bytes actually read.

A return value of less than bytesToRead indicates the end of the stream. Do _not
_ return from this callback until
either the entire bytesToRead is filled or you have reached the end of the strea
m.
*/
typedef size_t (* drwav_read_proc)(void* pUserData, void* pBufferOut, size_t byt
esToRead);

/*
Callback for when data is written. Returns value is the number of bytes actually
 written.

pUserData    [in]  The user data that was passed to drwav_init_write() and famil
y.
pData        [out] A pointer to the data to write.
bytesToWrite [in]  The number of bytes to write.

Returns the number of bytes actually written.

If the return value differs from bytesToWrite, it indicates an error.
*/
typedef size_t (* drwav_write_proc)(void* pUserData, const void* pData, size_t b
ytesToWrite);

/*
Callback for when data needs to be seeked.

pUserData [in] The user data that was passed to drwav_init() and family.
offset    [in] The number of bytes to move, relative to the origin. Will never b
e negative.
origin    [in] The origin of the seek - the current position or the start of the
 stream.

Returns whether or not the seek was successful.

Whether or not it is relative to the beginning or current position is determined
 by the "origin" parameter which will be either drwav_seek_origin_start or
drwav_seek_origin_current.
*/
typedef drwav_bool32 (* drwav_seek_proc)(void* pUserData, int offset, drwav_seek
_origin origin);

/*
Callback for when drwav_init_ex() finds a chunk.

pChunkUserData    [in] The user data that was passed to the pChunkUserData param
eter of drwav_init_ex() and family.
onRead            [in] A pointer to the function to call when reading.
onSeek            [in] A pointer to the function to call when seeking.
pReadSeekUserData [in] The user data that was passed to the pReadSeekUserData pa
rameter of drwav_init_ex() and family.
pChunkHeader      [in] A pointer to an object containing basic header informatio
n about the chunk. Use this to identify the chunk.
container         [in] Whether or not the WAV file is a RIFF or Wave64 container
. If you're unsure of the difference, assume RIFF.
pFMT              [in] A pointer to the object containing the contents of the "f
mt" chunk.

Returns the number of bytes read + seeked.

To read data from the chunk, call onRead(), passing in pReadSeekUserData as the
first parameter. Do the same for seeking with onSeek(). The return value must
be the total number of bytes you have read _plus_ seeked.

Use the `container` argument to discriminate the fields in `pChunkHeader->id`. I
f the container is `drwav_container_riff` or `drwav_container_rf64` you should
use `id.fourcc`, otherwise you should use `id.guid`.

The `pFMT` parameter can be used to determine the data format of the wave file.
Use `drwav_fmt_get_format()` to get the sample format, which will be one of the
`DR_WAVE_FORMAT_*` identifiers.

The read pointer will be sitting on the first byte after the chunk's header. You
 must not attempt to read beyond the boundary of the chunk.
*/
typedef drwav_uint64 (* drwav_chunk_proc)(void* pChunkUserData, drwav_read_proc
onRead, drwav_seek_proc onSeek, void* pReadSeekUserData, const drwav_chunk_heade
r* pChunkHeader, drwav_container container, const drwav_fmt* pFMT);


/* Structure for internal use. Only used for loaders opened with drwav_init_memo
ry(). */
typedef struct
{
    const drwav_uint8* data;
    size_t dataSize;
    size_t currentReadPos;
} drwav__memory_stream;

/* Structure for internal use. Only used for writers opened with drwav_init_memo
ry_write(). */
typedef struct
{
    void** ppData;
    size_t* pDataSize;
    size_t dataSize;
    size_t dataCapacity;
    size_t currentWritePos;
} drwav__memory_stream_write;

typedef struct
{
    drwav_container container;  /* RIFF, W64. */
    drwav_uint32 format;        /* DR_WAVE_FORMAT_* */
    drwav_uint32 channels;
    drwav_uint32 sampleRate;
    drwav_uint32 bitsPerSample;
} drwav_data_format;

typedef enum
{
    drwav_metadata_type_none                        = 0,

    /*
    Unknown simply means a chunk that drwav does not handle specifically. You ca
n still ask to
    receive these chunks as metadata objects. It is then up to you to interpret
the chunk's data.
    You can also write unknown metadata to a wav file. Be careful writing unknow
n chunks if you
    have also edited the audio data. The unknown chunks could represent offsets/
sizes that no
    longer correctly correspond to the audio data.
    */
    drwav_metadata_type_unknown                     = 1 << 0,

    /* Only 1 of each of these metadata items are allowed in a wav file. */
    drwav_metadata_type_smpl                        = 1 << 1,
    drwav_metadata_type_inst                        = 1 << 2,
    drwav_metadata_type_cue                         = 1 << 3,
    drwav_metadata_type_acid                        = 1 << 4,
    drwav_metadata_type_bext                        = 1 << 5,

    /*
    Wav files often have a LIST chunk. This is a chunk that contains a set of su
bchunks. For this
    higher-level metadata API, we don't make a distinction between a regular chu
nk and a LIST
    subchunk. Instead, they are all just 'metadata' items.

    There can be multiple of these metadata items in a wav file.
    */
    drwav_metadata_type_list_label                  = 1 << 6,
    drwav_metadata_type_list_note                   = 1 << 7,
    drwav_metadata_type_list_labelled_cue_region    = 1 << 8,

    drwav_metadata_type_list_info_software          = 1 << 9,
    drwav_metadata_type_list_info_copyright         = 1 << 10,
    drwav_metadata_type_list_info_title             = 1 << 11,
    drwav_metadata_type_list_info_artist            = 1 << 12,
    drwav_metadata_type_list_info_comment           = 1 << 13,
    drwav_metadata_type_list_info_date              = 1 << 14,
    drwav_metadata_type_list_info_genre             = 1 << 15,
    drwav_metadata_type_list_info_album             = 1 << 16,
    drwav_metadata_type_list_info_tracknumber       = 1 << 17,

    /* Other type constants for convenience. */
    drwav_metadata_type_list_all_info_strings       = drwav_metadata_type_list_i
nfo_software
                                                    | drwav_metadata_type_list_i
nfo_copyright
                                                    | drwav_metadata_type_list_i
nfo_title
                                                    | drwav_metadata_type_list_i
nfo_artist
                                                    | drwav_metadata_type_list_i
nfo_comment
                                                    | drwav_metadata_type_list_i
nfo_date
                                                    | drwav_metadata_type_list_i
nfo_genre
                                                    | drwav_metadata_type_list_i
nfo_album
                                                    | drwav_metadata_type_list_i
nfo_tracknumber,

    drwav_metadata_type_list_all_adtl               = drwav_metadata_type_list_l
abel
                                                    | drwav_metadata_type_list_n
ote
                                                    | drwav_metadata_type_list_l
abelled_cue_region,

    drwav_metadata_type_all                         = -2,   /*0xFFFFFFFF & ~drwa
v_metadata_type_unknown,*/
    drwav_metadata_type_all_including_unknown       = -1    /*0xFFFFFFFF,*/
} drwav_metadata_type;

/*
Sampler Metadata

The sampler chunk contains information about how a sound should be played in the
 context of a whole
audio production, and when used in a sampler. See https://en.wikipedia.org/wiki/
Sample-based_synthesis.
*/
typedef enum
{
    drwav_smpl_loop_type_forward  = 0,
    drwav_smpl_loop_type_pingpong = 1,
    drwav_smpl_loop_type_backward = 2
} drwav_smpl_loop_type;

typedef struct
{
    /* The ID of the associated cue point, see drwav_cue and drwav_cue_point. As
 with all cue point IDs, this can correspond to a label chunk to give this loop
a name, see drwav_list_label_or_note. */
    drwav_uint32 cuePointId;

    /* See drwav_smpl_loop_type. */
    drwav_uint32 type;

    /* The offset of the first sample to be played in the loop. */
    drwav_uint32 firstSampleOffset;

    /* The offset into the audio data of the last sample to be played in the loo
p. */
    drwav_uint32 lastSampleOffset;

    /* A value to represent that playback should occur at a point between sample
s. This value ranges from 0 to UINT32_MAX. Where a value of 0 means no fraction,
 and a value of (UINT32_MAX / 2) would mean half a sample. */
    drwav_uint32 sampleFraction;

    /* Number of times to play the loop. 0 means loop infinitely. */
    drwav_uint32 playCount;
} drwav_smpl_loop;

typedef struct
{
    /* IDs for a particular MIDI manufacturer. 0 if not used. */
    drwav_uint32 manufacturerId;
    drwav_uint32 productId;

    /* The period of 1 sample in nanoseconds. */
    drwav_uint32 samplePeriodNanoseconds;

    /* The MIDI root note of this file. 0 to 127. */
    drwav_uint32 midiUnityNote;

    /* The fraction of a semitone up from the given MIDI note. This is a value f
rom 0 to UINT32_MAX, where 0 means no change and (UINT32_MAX / 2) is half a semi
tone (AKA 50 cents). */
    drwav_uint32 midiPitchFraction;

    /* Data relating to SMPTE standards which are used for syncing audio and vid
eo. 0 if not used. */
    drwav_uint32 smpteFormat;
    drwav_uint32 smpteOffset;

    /* drwav_smpl_loop loops. */
    drwav_uint32 sampleLoopCount;

    /* Optional sampler-specific data. */
    drwav_uint32 samplerSpecificDataSizeInBytes;

    drwav_smpl_loop* pLoops;
    drwav_uint8* pSamplerSpecificData;
} drwav_smpl;

/*
Instrument Metadata

The inst metadata contains data about how a sound should be played as part of an
 instrument. This
commonly read by samplers. See https://en.wikipedia.org/wiki/Sample-based_synthe
sis.
*/
typedef struct
{
    drwav_int8 midiUnityNote;   /* The root note of the audio as a MIDI note num
ber. 0 to 127. */
    drwav_int8 fineTuneCents;   /* -50 to +50 */
    drwav_int8 gainDecibels;    /* -64 to +64 */
    drwav_int8 lowNote;         /* 0 to 127 */
    drwav_int8 highNote;        /* 0 to 127 */
    drwav_int8 lowVelocity;     /* 1 to 127 */
    drwav_int8 highVelocity;    /* 1 to 127 */
} drwav_inst;

/*
Cue Metadata

Cue points are markers at specific points in the audio. They often come with an
associated piece of
drwav_list_label_or_note metadata which contains the text for the marker.
*/
typedef struct
{
    /* Unique identification value. */
    drwav_uint32 id;

    /* Set to 0. This is only relevant if there is a 'playlist' chunk - which is
 not supported by dr_wav. */
    drwav_uint32 playOrderPosition;

    /* Should always be "data". This represents the fourcc value of the chunk th
at this cue point corresponds to. dr_wav only supports a single data chunk so th
is should always be "data". */
    drwav_uint8 dataChunkId[4];

    /* Set to 0. This is only relevant if there is a wave list chunk. dr_wav, li
ke lots of readers/writers, do not support this. */
    drwav_uint32 chunkStart;

    /* Set to 0 for uncompressed formats. Else the last byte in compressed wave
data where decompression can begin to find the value of the corresponding sample
 value. */
    drwav_uint32 blockStart;

    /* For uncompressed formats this is the offset of the cue point into the aud
io data. For compressed formats this is relative to the block specified with blo
ckStart. */
    drwav_uint32 sampleOffset;
} drwav_cue_point;

typedef struct
{
    drwav_uint32 cuePointCount;
    drwav_cue_point *pCuePoints;
} drwav_cue;

/*
Acid Metadata

This chunk contains some information about the time signature and the tempo of t
he audio.
*/
typedef enum
{
    drwav_acid_flag_one_shot      = 1,  /* If this is not set, then it is a loop
 instead of a one-shot. */
    drwav_acid_flag_root_note_set = 2,
    drwav_acid_flag_stretch       = 4,
    drwav_acid_flag_disk_based    = 8,
    drwav_acid_flag_acidizer      = 16  /* Not sure what this means. */
} drwav_acid_flag;

typedef struct
{
    /* A bit-field, see drwav_acid_flag. */
    drwav_uint32 flags;

    /* Valid if flags contains drwav_acid_flag_root_note_set. It represents the
MIDI root note the file - a value from 0 to 127. */
    drwav_uint16 midiUnityNote;

    /* Reserved values that should probably be ignored. reserved1 seems to often
 be 128 and reserved2 is 0. */
    drwav_uint16 reserved1;
    float reserved2;

    /* Number of beats. */
    drwav_uint32 numBeats;

    /* The time signature of the audio. */
    drwav_uint16 meterDenominator;
    drwav_uint16 meterNumerator;

    /* Beats per minute of the track. Setting a value of 0 suggests that there i
s no tempo. */
    float tempo;
} drwav_acid;

/*
Cue Label or Note metadata

These are 2 different types of metadata, but they have the exact same format. La
bels tend to be the
more common and represent a short name for a cue point. Notes might be used to r
epresent a longer
comment.
*/
typedef struct
{
    /* The ID of a cue point that this label or note corresponds to. */
    drwav_uint32 cuePointId;

    /* Size of the string not including any null terminator. */
    drwav_uint32 stringLength;

    /* The string. The *init_with_metadata functions null terminate this for con
venience. */
    char* pString;
} drwav_list_label_or_note;

/*
BEXT metadata, also known as Broadcast Wave Format (BWF)

This metadata adds some extra description to an audio file. You must check the v
ersion field to
determine if the UMID or the loudness fields are valid.
*/
typedef struct
{
    /*
    These top 3 fields, and the umid field are actually defined in the standard
as a statically
    sized buffers. In order to reduce the size of this struct (and therefore the
 union in the
    metadata struct), we instead store these as pointers.
    */
    char* pDescription;                 /* Can be NULL or a null-terminated stri
ng, must be <= 256 characters. */
    char* pOriginatorName;              /* Can be NULL or a null-terminated stri
ng, must be <= 32 characters. */
    char* pOriginatorReference;         /* Can be NULL or a null-terminated stri
ng, must be <= 32 characters. */
    char  pOriginationDate[10];         /* ASCII "yyyy:mm:dd". */
    char  pOriginationTime[8];          /* ASCII "hh:mm:ss". */
    drwav_uint64 timeReference;         /* First sample count since midnight. */
    drwav_uint16 version;               /* Version of the BWF, check this to see
 if the fields below are valid. */

    /*
    Unrestricted ASCII characters containing a collection of strings terminated
by CR/LF. Each
    string shall contain a description of a coding process applied to the audio
data.
    */
    char* pCodingHistory;
    drwav_uint32 codingHistorySize;

    /* Fields below this point are only valid if the version is 1 or above. */
    drwav_uint8* pUMID;                  /* Exactly 64 bytes of SMPTE UMID */

    /* Fields below this point are only valid if the version is 2 or above. */
    drwav_uint16 loudnessValue;         /* Integrated Loudness Value of the file
 in LUFS (multiplied by 100). */
    drwav_uint16 loudnessRange;         /* Loudness Range of the file in LU (mul
tiplied by 100). */
    drwav_uint16 maxTruePeakLevel;      /* Maximum True Peak Level of the file e
xpressed as dBTP (multiplied by 100). */
    drwav_uint16 maxMomentaryLoudness;  /* Highest value of the Momentary Loudne
ss Level of the file in LUFS (multiplied by 100). */
    drwav_uint16 maxShortTermLoudness;  /* Highest value of the Short-Term Loudn
ess Level of the file in LUFS (multiplied by 100). */
} drwav_bext;

/*
Info Text Metadata

There a many different types of information text that can be saved in this forma
t. This is where
things like the album name, the artists, the year it was produced, etc are saved
. See
drwav_metadata_type for the full list of types that dr_wav supports.
*/
typedef struct
{
    /* Size of the string not including any null terminator. */
    drwav_uint32 stringLength;

    /* The string. The *init_with_metadata functions null terminate this for con
venience. */
    char* pString;
} drwav_list_info_text;

/*
Labelled Cue Region Metadata

The labelled cue region metadata is used to associate some region of audio with
text. The region
starts at a cue point, and extends for the given number of samples.
*/
typedef struct
{
    /* The ID of a cue point that this object corresponds to. */
    drwav_uint32 cuePointId;

    /* The number of samples from the cue point forwards that should be consider
ed this region */
    drwav_uint32 sampleLength;

    /* Four characters used to say what the purpose of this region is. */
    drwav_uint8 purposeId[4];

    /* Unsure of the exact meanings of these. It appears to be acceptable to set
 them all to 0. */
    drwav_uint16 country;
    drwav_uint16 language;
    drwav_uint16 dialect;
    drwav_uint16 codePage;

    /* Size of the string not including any null terminator. */
    drwav_uint32 stringLength;

    /* The string. The *init_with_metadata functions null terminate this for con
venience. */
    char* pString;
} drwav_list_labelled_cue_region;

/*
Unknown Metadata

This chunk just represents a type of chunk that dr_wav does not understand.

Unknown metadata has a location attached to it. This is because wav files can ha
ve a LIST chunk
that contains subchunks. These LIST chunks can be one of two types. An adtl list
, or an INFO
list. This enum is used to specify the location of a chunk that dr_wav currently
 doesn't support.
*/
typedef enum
{
    drwav_metadata_location_invalid,
    drwav_metadata_location_top_level,
    drwav_metadata_location_inside_info_list,
    drwav_metadata_location_inside_adtl_list
} drwav_metadata_location;

typedef struct
{
    drwav_uint8 id[4];
    drwav_metadata_location chunkLocation;
    drwav_uint32 dataSizeInBytes;
    drwav_uint8* pData;
} drwav_unknown_metadata;

/*
Metadata is saved as a union of all the supported types.
*/
typedef struct
{
    /* Determines which item in the union is valid. */
    drwav_metadata_type type;

    union
    {
        drwav_cue cue;
        drwav_smpl smpl;
        drwav_acid acid;
        drwav_inst inst;
        drwav_bext bext;
        drwav_list_label_or_note labelOrNote;   /* List label or list note. */
        drwav_list_labelled_cue_region labelledCueRegion;
        drwav_list_info_text infoText;          /* Any of the list info types. *
/
        drwav_unknown_metadata unknown;
    } data;
} drwav_metadata;

typedef struct
{
    /* A pointer to the function to call when more data is needed. */
    drwav_read_proc onRead;

    /* A pointer to the function to call when data needs to be written. Only use
d when the drwav object is opened in write mode. */
    drwav_write_proc onWrite;

    /* A pointer to the function to call when the wav file needs to be seeked. *
/
    drwav_seek_proc onSeek;

    /* The user data to pass to callbacks. */
    void* pUserData;

    /* Allocation callbacks. */
    drwav_allocation_callbacks allocationCallbacks;


    /* Whether or not the WAV file is formatted as a standard RIFF file or W64.
*/
    drwav_container container;


    /* Structure containing format information exactly as specified by the wav f
ile. */
    drwav_fmt fmt;

    /* The sample rate. Will be set to something like 44100. */
    drwav_uint32 sampleRate;

    /* The number of channels. This will be set to 1 for monaural streams, 2 for
 stereo, etc. */
    drwav_uint16 channels;

    /* The bits per sample. Will be set to something like 16, 24, etc. */
    drwav_uint16 bitsPerSample;

    /* Equal to fmt.formatTag, or the value specified by fmt.subFormat if fmt.fo
rmatTag is equal to 65534 (WAVE_FORMAT_EXTENSIBLE). */
    drwav_uint16 translatedFormatTag;

    /* The total number of PCM frames making up the audio data. */
    drwav_uint64 totalPCMFrameCount;


    /* The size in bytes of the data chunk. */
    drwav_uint64 dataChunkDataSize;

    /* The position in the stream of the first data byte of the data chunk. This
 is used for seeking. */
    drwav_uint64 dataChunkDataPos;

    /* The number of bytes remaining in the data chunk. */
    drwav_uint64 bytesRemaining;

    /* The current read position in PCM frames. */
    drwav_uint64 readCursorInPCMFrames;


    /*
    Only used in sequential write mode. Keeps track of the desired size of the "
data" chunk at the point of initialization time. Always
    set to 0 for non-sequential writes and when the drwav object is opened in re
ad mode. Used for validation.
    */
    drwav_uint64 dataChunkDataSizeTargetWrite;

    /* Keeps track of whether or not the wav writer was initialized in sequentia
l mode. */
    drwav_bool32 isSequentialWrite;


    /* A array of metadata. This is valid after the *init_with_metadata call ret
urns. It will be valid until drwav_uninit() is called. You can take ownership of
 this data with drwav_take_ownership_of_metadata(). */
    drwav_metadata* pMetadata;
    drwav_uint32 metadataCount;


    /* A hack to avoid a DRWAV_MALLOC() when opening a decoder with drwav_init_m
emory(). */
    drwav__memory_stream memoryStream;
    drwav__memory_stream_write memoryStreamWrite;


    /* Microsoft ADPCM specific data. */
    struct
    {
        drwav_uint32 bytesRemainingInBlock;
        drwav_uint16 predictor[2];
        drwav_int32  delta[2];
        drwav_int32  cachedFrames[4];  /* Samples are stored in this cache durin
g decoding. */
        drwav_uint32 cachedFrameCount;
        drwav_int32  prevFrames[2][2]; /* The previous 2 samples for each channe
l (2 channels at most). */
    } msadpcm;

    /* IMA ADPCM specific data. */
    struct
    {
        drwav_uint32 bytesRemainingInBlock;
        drwav_int32  predictor[2];
        drwav_int32  stepIndex[2];
        drwav_int32  cachedFrames[16]; /* Samples are stored in this cache durin
g decoding. */
        drwav_uint32 cachedFrameCount;
    } ima;

    /* AIFF specific data. */
    struct
    {
        drwav_bool8 isLE;   /* Will be set to true if the audio data is little-e
ndian encoded. */
        drwav_bool8 isUnsigned; /* Only used for 8-bit samples. When set to true
, will be treated as unsigned. */
    } aiff;
} drwav;


/*
Initializes a pre-allocated drwav object for reading.

pWav                         [out]          A pointer to the drwav object being
initialized.
onRead                       [in]           The function to call when data needs
 to be read from the client.
onSeek                       [in]           The function to call when the read p
osition of the client data needs to move.
onChunk                      [in, optional] The function to call when a chunk is
 enumerated at initialized time.
pUserData, pReadSeekUserData [in, optional] A pointer to application defined dat
a that will be passed to onRead and onSeek.
pChunkUserData               [in, optional] A pointer to application defined dat
a that will be passed to onChunk.
flags                        [in, optional] A set of flags for controlling how t
hings are loaded.

Returns true if successful; false otherwise.

Close the loader with drwav_uninit().

This is the lowest level function for initializing a WAV file. You can also use
drwav_init_file() and drwav_init_memory()
to open the stream from a file or from a block of memory respectively.

Possible values for flags:
  DRWAV_SEQUENTIAL: Never perform a backwards seek while loading. This disables
the chunk callback and will cause this function
                    to return as soon as the data chunk is found. Any chunks aft
er the data chunk will be ignored.

drwav_init() is equivalent to "drwav_init_ex(pWav, onRead, onSeek, NULL, pUserDa
ta, NULL, 0);".

The onChunk callback is not called for the WAVE or FMT chunks. The contents of t
he FMT chunk can be read from pWav->fmt
after the function returns.

See also: drwav_init_file(), drwav_init_memory(), drwav_uninit()
*/
DRWAV_API drwav_bool32 drwav_init(drwav* pWav, drwav_read_proc onRead, drwav_see
k_proc onSeek, void* pUserData, const drwav_allocation_callbacks* pAllocationCal
lbacks);
DRWAV_API drwav_bool32 drwav_init_ex(drwav* pWav, drwav_read_proc onRead, drwav_
seek_proc onSeek, drwav_chunk_proc onChunk, void* pReadSeekUserData, void* pChun
kUserData, drwav_uint32 flags, const drwav_allocation_callbacks* pAllocationCall
backs);
DRWAV_API drwav_bool32 drwav_init_with_metadata(drwav* pWav, drwav_read_proc onR
ead, drwav_seek_proc onSeek, void* pUserData, drwav_uint32 flags, const drwav_al
location_callbacks* pAllocationCallbacks);

/*
Initializes a pre-allocated drwav object for writing.

onWrite               [in]           The function to call when data needs to be
written.
onSeek                [in]           The function to call when the write positio
n needs to move.
pUserData             [in, optional] A pointer to application defined data that
will be passed to onWrite and onSeek.
metadata, numMetadata [in, optional] An array of metadata objects that should be
 written to the file. The array is not edited. You are responsible for this meta
data memory and it must maintain valid until drwav_uninit() is called.

Returns true if successful; false otherwise.

Close the writer with drwav_uninit().

This is the lowest level function for initializing a WAV file. You can also use
drwav_init_file_write() and drwav_init_memory_write()
to open the stream from a file or from a block of memory respectively.

If the total sample count is known, you can use drwav_init_write_sequential(). T
his avoids the need for dr_wav to perform
a post-processing step for storing the total sample count and the size of the da
ta chunk which requires a backwards seek.

See also: drwav_init_file_write(), drwav_init_memory_write(), drwav_uninit()
*/
DRWAV_API drwav_bool32 drwav_init_write(drwav* pWav, const drwav_data_format* pF
ormat, drwav_write_proc onWrite, drwav_seek_proc onSeek, void* pUserData, const
drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_write_sequential(drwav* pWav, const drwav_data
_format* pFormat, drwav_uint64 totalSampleCount, drwav_write_proc onWrite, void*
 pUserData, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_write_sequential_pcm_frames(drwav* pWav, const
 drwav_data_format* pFormat, drwav_uint64 totalPCMFrameCount, drwav_write_proc o
nWrite, void* pUserData, const drwav_allocation_callbacks* pAllocationCallbacks)
;
DRWAV_API drwav_bool32 drwav_init_write_with_metadata(drwav* pWav, const drwav_d
ata_format* pFormat, drwav_write_proc onWrite, drwav_seek_proc onSeek, void* pUs
erData, const drwav_allocation_callbacks* pAllocationCallbacks, drwav_metadata*
pMetadata, drwav_uint32 metadataCount);

/*
Utility function to determine the target size of the entire data to be written (
including all headers and chunks).

Returns the target size in bytes.

The metadata argument can be NULL meaning no metadata exists.

Useful if the application needs to know the size to allocate.

Only writing to the RIFF chunk and one data chunk is currently supported.

See also: drwav_init_write(), drwav_init_file_write(), drwav_init_memory_write()
*/
DRWAV_API drwav_uint64 drwav_target_write_size_bytes(const drwav_data_format* pF
ormat, drwav_uint64 totalFrameCount, drwav_metadata* pMetadata, drwav_uint32 met
adataCount);

/*
Take ownership of the metadata objects that were allocated via one of the init_w
ith_metadata() function calls. The init_with_metdata functions perform a single
heap allocation for this metadata.

Useful if you want the data to persist beyond the lifetime of the drwav object.

You must free the data returned from this function using drwav_free().
*/
DRWAV_API drwav_metadata* drwav_take_ownership_of_metadata(drwav* pWav);

/*
Uninitializes the given drwav object.

Use this only for objects initialized with drwav_init*() functions (drwav_init()
, drwav_init_ex(), drwav_init_write(), drwav_init_write_sequential()).
*/
DRWAV_API drwav_result drwav_uninit(drwav* pWav);


/*
Reads raw audio data.

This is the lowest level function for reading audio data. It simply reads the gi
ven number of
bytes of the raw internal sample data.

Consider using drwav_read_pcm_frames_s16(), drwav_read_pcm_frames_s32() or drwav
_read_pcm_frames_f32() for
reading sample data in a consistent format.

pBufferOut can be NULL in which case a seek will be performed.

Returns the number of bytes actually read.
*/
DRWAV_API size_t drwav_read_raw(drwav* pWav, size_t bytesToRead, void* pBufferOu
t);

/*
Reads up to the specified number of PCM frames from the WAV file.

The output data will be in the file's internal format, converted to native-endia
n byte order. Use
drwav_read_pcm_frames_s16/f32/s32() to read data in a specific format.

If the return value is less than <framesToRead> it means the end of the file has
 been reached or
you have requested more PCM frames than can possibly fit in the output buffer.

This function will only work when sample data is of a fixed size and uncompresse
d. If you are
using a compressed format consider using drwav_read_raw() or drwav_read_pcm_fram
es_s16/s32/f32().

pBufferOut can be NULL in which case a seek will be performed.
*/
DRWAV_API drwav_uint64 drwav_read_pcm_frames(drwav* pWav, drwav_uint64 framesToR
ead, void* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_le(drwav* pWav, drwav_uint64 frames
ToRead, void* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_be(drwav* pWav, drwav_uint64 frames
ToRead, void* pBufferOut);

/*
Seeks to the given PCM frame.

Returns true if successful; false otherwise.
*/
DRWAV_API drwav_bool32 drwav_seek_to_pcm_frame(drwav* pWav, drwav_uint64 targetF
rameIndex);

/*
Retrieves the current read position in pcm frames.
*/
DRWAV_API drwav_result drwav_get_cursor_in_pcm_frames(drwav* pWav, drwav_uint64*
 pCursor);

/*
Retrieves the length of the file.
*/
DRWAV_API drwav_result drwav_get_length_in_pcm_frames(drwav* pWav, drwav_uint64*
 pLength);


/*
Writes raw audio data.

Returns the number of bytes actually written. If this differs from bytesToWrite,
 it indicates an error.
*/
DRWAV_API size_t drwav_write_raw(drwav* pWav, size_t bytesToWrite, const void* p
Data);

/*
Writes PCM frames.

Returns the number of PCM frames written.

Input samples need to be in native-endian byte order. On big-endian architecture
s the input data will be converted to
little-endian. Use drwav_write_raw() to write raw audio data without performing
any conversion.
*/
DRWAV_API drwav_uint64 drwav_write_pcm_frames(drwav* pWav, drwav_uint64 framesTo
Write, const void* pData);
DRWAV_API drwav_uint64 drwav_write_pcm_frames_le(drwav* pWav, drwav_uint64 frame
sToWrite, const void* pData);
DRWAV_API drwav_uint64 drwav_write_pcm_frames_be(drwav* pWav, drwav_uint64 frame
sToWrite, const void* pData);

/* Conversion Utilities */
#ifndef DR_WAV_NO_CONVERSION_API

/*
Reads a chunk of audio data and converts it to signed 16-bit PCM samples.

pBufferOut can be NULL in which case a seek will be performed.

Returns the number of PCM frames actually read.

If the return value is less than <framesToRead> it means the end of the file has
 been reached.
*/
DRWAV_API drwav_uint64 drwav_read_pcm_frames_s16(drwav* pWav, drwav_uint64 frame
sToRead, drwav_int16* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_s16le(drwav* pWav, drwav_uint64 fra
mesToRead, drwav_int16* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_s16be(drwav* pWav, drwav_uint64 fra
mesToRead, drwav_int16* pBufferOut);

/* Low-level function for converting unsigned 8-bit PCM samples to signed 16-bit
 PCM samples. */
DRWAV_API void drwav_u8_to_s16(drwav_int16* pOut, const drwav_uint8* pIn, size_t
 sampleCount);

/* Low-level function for converting signed 24-bit PCM samples to signed 16-bit
PCM samples. */
DRWAV_API void drwav_s24_to_s16(drwav_int16* pOut, const drwav_uint8* pIn, size_
t sampleCount);

/* Low-level function for converting signed 32-bit PCM samples to signed 16-bit
PCM samples. */
DRWAV_API void drwav_s32_to_s16(drwav_int16* pOut, const drwav_int32* pIn, size_
t sampleCount);

/* Low-level function for converting IEEE 32-bit floating point samples to signe
d 16-bit PCM samples. */
DRWAV_API void drwav_f32_to_s16(drwav_int16* pOut, const float* pIn, size_t samp
leCount);

/* Low-level function for converting IEEE 64-bit floating point samples to signe
d 16-bit PCM samples. */
DRWAV_API void drwav_f64_to_s16(drwav_int16* pOut, const double* pIn, size_t sam
pleCount);

/* Low-level function for converting A-law samples to signed 16-bit PCM samples.
 */
DRWAV_API void drwav_alaw_to_s16(drwav_int16* pOut, const drwav_uint8* pIn, size
_t sampleCount);

/* Low-level function for converting u-law samples to signed 16-bit PCM samples.
 */
DRWAV_API void drwav_mulaw_to_s16(drwav_int16* pOut, const drwav_uint8* pIn, siz
e_t sampleCount);


/*
Reads a chunk of audio data and converts it to IEEE 32-bit floating point sample
s.

pBufferOut can be NULL in which case a seek will be performed.

Returns the number of PCM frames actually read.

If the return value is less than <framesToRead> it means the end of the file has
 been reached.
*/
DRWAV_API drwav_uint64 drwav_read_pcm_frames_f32(drwav* pWav, drwav_uint64 frame
sToRead, float* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_f32le(drwav* pWav, drwav_uint64 fra
mesToRead, float* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_f32be(drwav* pWav, drwav_uint64 fra
mesToRead, float* pBufferOut);

/* Low-level function for converting unsigned 8-bit PCM samples to IEEE 32-bit f
loating point samples. */
DRWAV_API void drwav_u8_to_f32(float* pOut, const drwav_uint8* pIn, size_t sampl
eCount);

/* Low-level function for converting signed 16-bit PCM samples to IEEE 32-bit fl
oating point samples. */
DRWAV_API void drwav_s16_to_f32(float* pOut, const drwav_int16* pIn, size_t samp
leCount);

/* Low-level function for converting signed 24-bit PCM samples to IEEE 32-bit fl
oating point samples. */
DRWAV_API void drwav_s24_to_f32(float* pOut, const drwav_uint8* pIn, size_t samp
leCount);

/* Low-level function for converting signed 32-bit PCM samples to IEEE 32-bit fl
oating point samples. */
DRWAV_API void drwav_s32_to_f32(float* pOut, const drwav_int32* pIn, size_t samp
leCount);

/* Low-level function for converting IEEE 64-bit floating point samples to IEEE
32-bit floating point samples. */
DRWAV_API void drwav_f64_to_f32(float* pOut, const double* pIn, size_t sampleCou
nt);

/* Low-level function for converting A-law samples to IEEE 32-bit floating point
 samples. */
DRWAV_API void drwav_alaw_to_f32(float* pOut, const drwav_uint8* pIn, size_t sam
pleCount);

/* Low-level function for converting u-law samples to IEEE 32-bit floating point
 samples. */
DRWAV_API void drwav_mulaw_to_f32(float* pOut, const drwav_uint8* pIn, size_t sa
mpleCount);


/*
Reads a chunk of audio data and converts it to signed 32-bit PCM samples.

pBufferOut can be NULL in which case a seek will be performed.

Returns the number of PCM frames actually read.

If the return value is less than <framesToRead> it means the end of the file has
 been reached.
*/
DRWAV_API drwav_uint64 drwav_read_pcm_frames_s32(drwav* pWav, drwav_uint64 frame
sToRead, drwav_int32* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_s32le(drwav* pWav, drwav_uint64 fra
mesToRead, drwav_int32* pBufferOut);
DRWAV_API drwav_uint64 drwav_read_pcm_frames_s32be(drwav* pWav, drwav_uint64 fra
mesToRead, drwav_int32* pBufferOut);

/* Low-level function for converting unsigned 8-bit PCM samples to signed 32-bit
 PCM samples. */
DRWAV_API void drwav_u8_to_s32(drwav_int32* pOut, const drwav_uint8* pIn, size_t
 sampleCount);

/* Low-level function for converting signed 16-bit PCM samples to signed 32-bit
PCM samples. */
DRWAV_API void drwav_s16_to_s32(drwav_int32* pOut, const drwav_int16* pIn, size_
t sampleCount);

/* Low-level function for converting signed 24-bit PCM samples to signed 32-bit
PCM samples. */
DRWAV_API void drwav_s24_to_s32(drwav_int32* pOut, const drwav_uint8* pIn, size_
t sampleCount);

/* Low-level function for converting IEEE 32-bit floating point samples to signe
d 32-bit PCM samples. */
DRWAV_API void drwav_f32_to_s32(drwav_int32* pOut, const float* pIn, size_t samp
leCount);

/* Low-level function for converting IEEE 64-bit floating point samples to signe
d 32-bit PCM samples. */
DRWAV_API void drwav_f64_to_s32(drwav_int32* pOut, const double* pIn, size_t sam
pleCount);

/* Low-level function for converting A-law samples to signed 32-bit PCM samples.
 */
DRWAV_API void drwav_alaw_to_s32(drwav_int32* pOut, const drwav_uint8* pIn, size
_t sampleCount);

/* Low-level function for converting u-law samples to signed 32-bit PCM samples.
 */
DRWAV_API void drwav_mulaw_to_s32(drwav_int32* pOut, const drwav_uint8* pIn, siz
e_t sampleCount);

#endif  /* DR_WAV_NO_CONVERSION_API */


/* High-Level Convenience Helpers */

#ifndef DR_WAV_NO_STDIO
/*
Helper for initializing a wave file for reading using stdio.

This holds the internal FILE object until drwav_uninit() is called. Keep this in
 mind if you're caching drwav
objects because the operating system may restrict the number of file handles an
application can have open at
any given time.
*/
DRWAV_API drwav_bool32 drwav_init_file(drwav* pWav, const char* filename, const
drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_ex(drwav* pWav, const char* filename, drw
av_chunk_proc onChunk, void* pChunkUserData, drwav_uint32 flags, const drwav_all
ocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_w(drwav* pWav, const wchar_t* filename, c
onst drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_ex_w(drwav* pWav, const wchar_t* filename
, drwav_chunk_proc onChunk, void* pChunkUserData, drwav_uint32 flags, const drwa
v_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_with_metadata(drwav* pWav, const char* fi
lename, drwav_uint32 flags, const drwav_allocation_callbacks* pAllocationCallbac
ks);
DRWAV_API drwav_bool32 drwav_init_file_with_metadata_w(drwav* pWav, const wchar_
t* filename, drwav_uint32 flags, const drwav_allocation_callbacks* pAllocationCa
llbacks);


/*
Helper for initializing a wave file for writing using stdio.

This holds the internal FILE object until drwav_uninit() is called. Keep this in
 mind if you're caching drwav
objects because the operating system may restrict the number of file handles an
application can have open at
any given time.
*/
DRWAV_API drwav_bool32 drwav_init_file_write(drwav* pWav, const char* filename,
const drwav_data_format* pFormat, const drwav_allocation_callbacks* pAllocationC
allbacks);
DRWAV_API drwav_bool32 drwav_init_file_write_sequential(drwav* pWav, const char*
 filename, const drwav_data_format* pFormat, drwav_uint64 totalSampleCount, cons
t drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_write_sequential_pcm_frames(drwav* pWav,
const char* filename, const drwav_data_format* pFormat, drwav_uint64 totalPCMFra
meCount, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_write_w(drwav* pWav, const wchar_t* filen
ame, const drwav_data_format* pFormat, const drwav_allocation_callbacks* pAlloca
tionCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_write_sequential_w(drwav* pWav, const wch
ar_t* filename, const drwav_data_format* pFormat, drwav_uint64 totalSampleCount,
 const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_file_write_sequential_pcm_frames_w(drwav* pWav
, const wchar_t* filename, const drwav_data_format* pFormat, drwav_uint64 totalP
CMFrameCount, const drwav_allocation_callbacks* pAllocationCallbacks);
#endif  /* DR_WAV_NO_STDIO */

/*
Helper for initializing a loader from a pre-allocated memory buffer.

This does not create a copy of the data. It is up to the application to ensure t
he buffer remains valid for
the lifetime of the drwav object.

The buffer should contain the contents of the entire wave file, not just the sam
ple data.
*/
DRWAV_API drwav_bool32 drwav_init_memory(drwav* pWav, const void* data, size_t d
ataSize, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_memory_ex(drwav* pWav, const void* data, size_
t dataSize, drwav_chunk_proc onChunk, void* pChunkUserData, drwav_uint32 flags,
const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_memory_with_metadata(drwav* pWav, const void*
data, size_t dataSize, drwav_uint32 flags, const drwav_allocation_callbacks* pAl
locationCallbacks);

/*
Helper for initializing a writer which outputs data to a memory buffer.

dr_wav will manage the memory allocations, however it is up to the caller to fre
e the data with drwav_free().

The buffer will remain allocated even after drwav_uninit() is called. The buffer
 should not be considered valid
until after drwav_uninit() has been called.
*/
DRWAV_API drwav_bool32 drwav_init_memory_write(drwav* pWav, void** ppData, size_
t* pDataSize, const drwav_data_format* pFormat, const drwav_allocation_callbacks
* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_memory_write_sequential(drwav* pWav, void** pp
Data, size_t* pDataSize, const drwav_data_format* pFormat, drwav_uint64 totalSam
pleCount, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_bool32 drwav_init_memory_write_sequential_pcm_frames(drwav* pWav
, void** ppData, size_t* pDataSize, const drwav_data_format* pFormat, drwav_uint
64 totalPCMFrameCount, const drwav_allocation_callbacks* pAllocationCallbacks);


#ifndef DR_WAV_NO_CONVERSION_API
/*
Opens and reads an entire wav file in a single operation.

The return value is a heap-allocated buffer containing the audio data. Use drwav
_free() to free the buffer.
*/
DRWAV_API drwav_int16* drwav_open_and_read_pcm_frames_s16(drwav_read_proc onRead
, drwav_seek_proc onSeek, void* pUserData, unsigned int* channelsOut, unsigned i
nt* sampleRateOut, drwav_uint64* totalFrameCountOut, const drwav_allocation_call
backs* pAllocationCallbacks);
DRWAV_API float* drwav_open_and_read_pcm_frames_f32(drwav_read_proc onRead, drwa
v_seek_proc onSeek, void* pUserData, unsigned int* channelsOut, unsigned int* sa
mpleRateOut, drwav_uint64* totalFrameCountOut, const drwav_allocation_callbacks*
 pAllocationCallbacks);
DRWAV_API drwav_int32* drwav_open_and_read_pcm_frames_s32(drwav_read_proc onRead
, drwav_seek_proc onSeek, void* pUserData, unsigned int* channelsOut, unsigned i
nt* sampleRateOut, drwav_uint64* totalFrameCountOut, const drwav_allocation_call
backs* pAllocationCallbacks);
#ifndef DR_WAV_NO_STDIO
/*
Opens and decodes an entire wav file in a single operation.

The return value is a heap-allocated buffer containing the audio data. Use drwav
_free() to free the buffer.
*/
DRWAV_API drwav_int16* drwav_open_file_and_read_pcm_frames_s16(const char* filen
ame, unsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint64* total
FrameCountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API float* drwav_open_file_and_read_pcm_frames_f32(const char* filename, u
nsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint64* totalFrameC
ountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_int32* drwav_open_file_and_read_pcm_frames_s32(const char* filen
ame, unsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint64* total
FrameCountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_int16* drwav_open_file_and_read_pcm_frames_s16_w(const wchar_t*
filename, unsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint64*
totalFrameCountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API float* drwav_open_file_and_read_pcm_frames_f32_w(const wchar_t* filena
me, unsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint64* totalF
rameCountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_int32* drwav_open_file_and_read_pcm_frames_s32_w(const wchar_t*
filename, unsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint64*
totalFrameCountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
#endif
/*
Opens and decodes an entire wav file from a block of memory in a single operatio
n.

The return value is a heap-allocated buffer containing the audio data. Use drwav
_free() to free the buffer.
*/
DRWAV_API drwav_int16* drwav_open_memory_and_read_pcm_frames_s16(const void* dat
a, size_t dataSize, unsigned int* channelsOut, unsigned int* sampleRateOut, drwa
v_uint64* totalFrameCountOut, const drwav_allocation_callbacks* pAllocationCallb
acks);
DRWAV_API float* drwav_open_memory_and_read_pcm_frames_f32(const void* data, siz
e_t dataSize, unsigned int* channelsOut, unsigned int* sampleRateOut, drwav_uint
64* totalFrameCountOut, const drwav_allocation_callbacks* pAllocationCallbacks);
DRWAV_API drwav_int32* drwav_open_memory_and_read_pcm_frames_s32(const void* dat
a, size_t dataSize, unsigned int* channelsOut, unsigned int* sampleRateOut, drwa
v_uint64* totalFrameCountOut, const drwav_allocation_callbacks* pAllocationCallb
acks);
#endif

/* Frees data that was allocated internally by dr_wav. */
DRWAV_API void drwav_free(void* p, const drwav_allocation_callbacks* pAllocation
Callbacks);

/* Converts bytes from a wav stream to a sized type of native endian. */
DRWAV_API drwav_uint16 drwav_bytes_to_u16(const drwav_uint8* data);
DRWAV_API drwav_int16 drwav_bytes_to_s16(const drwav_uint8* data);
DRWAV_API drwav_uint32 drwav_bytes_to_u32(const drwav_uint8* data);
DRWAV_API drwav_int32 drwav_bytes_to_s32(const drwav_uint8* data);
DRWAV_API drwav_uint64 drwav_bytes_to_u64(const drwav_uint8* data);
DRWAV_API drwav_int64 drwav_bytes_to_s64(const drwav_uint8* data);
DRWAV_API float drwav_bytes_to_f32(const drwav_uint8* data);

/* Compares a GUID for the purpose of checking the type of a Wave64 chunk. */
DRWAV_API drwav_bool32 drwav_guid_equal(const drwav_uint8 a[16], const drwav_uin
t8 b[16]);

/* Compares a four-character-code for the purpose of checking the type of a RIFF
 chunk. */
DRWAV_API drwav_bool32 drwav_fourcc_equal(const drwav_uint8* a, const char* b);

#ifdef __cplusplus
}
#endif
#endif  /* dr_wav_h */


/*******************************************************************************
*****************************************************************************
 *******************************************************************************
*****************************************************************************

 IMPLEMENTATION

 *******************************************************************************
*****************************************************************************
 *******************************************************************************
*****************************************************************************/
#if defined(DR_WAV_IMPLEMENTATION) || defined(DRWAV_IMPLEMENTATION)
#ifndef dr_wav_c
#define dr_wav_c

#ifdef __MRC__
/* MrC currently doesn't compile dr_wav correctly with any optimizations enabled
. */
#pragma options opt off
#endif

#include <stdlib.h>
#include <string.h>
#include <limits.h> /* For INT_MAX */

#ifndef DR_WAV_NO_STDIO
#include <stdio.h>
#ifndef DR_WAV_NO_WCHAR
#include <wchar.h>
#endif
#endif

/* Standard library stuff. */
#ifndef DRWAV_ASSERT
#include <assert.h>
#define DRWAV_ASSERT(expression)           assert(expression)
#endif
#ifndef DRWAV_MALLOC
#define DRWAV_MALLOC(sz)                   malloc((sz))
#endif
#ifndef DRWAV_REALLOC
#define DRWAV_REALLOC(p, sz)               realloc((p), (sz))
#endif
#ifndef DRWAV_FREE
#define DRWAV_FREE(p)                      free((p))
#endif
#ifndef DRWAV_COPY_MEMORY
#define DRWAV_COPY_MEMORY(dst, src, sz)    memcpy((dst), (src), (sz))
#endif
#ifndef DRWAV_ZERO_MEMORY
#define DRWAV_ZERO_MEMORY(p, sz)           memset((p), 0, (sz))
#endif
#ifndef DRWAV_ZERO_OBJECT
#define DRWAV_ZERO_OBJECT(p)               DRWAV_ZERO_MEMORY((p), sizeof(*p))
#endif

#define drwav_countof(x)                   (sizeof(x) / sizeof(x[0]))
#define drwav_align(x, a)                  ((((x) + (a) - 1) / (a)) * (a))
#define drwav_min(a, b)                    (((a) < (b)) ? (a) : (b))
#define drwav_max(a, b)                    (((a) > (b)) ? (a) : (b))
#define drwav_clamp(x, lo, hi)             (drwav_max((lo), drwav_min((hi), (x))
))
#define drwav_offset_ptr(p, offset)        (((drwav_uint8*)(p)) + (offset))

#define DRWAV_MAX_SIMD_VECTOR_SIZE         32

/* Architecture Detection */
#if defined(__x86_64__) || (defined(_M_X64) && !defined(_M_ARM64EC))
    #define DRWAV_X64
#elif defined(__i386) || defined(_M_IX86)
    #define DRWAV_X86
#elif defined(__arm__) || defined(_M_ARM)
    #define DRWAV_ARM
#endif
/* End Architecture Detection */

/* Inline */
#ifdef _MSC_VER
    #define DRWAV_INLINE __forceinline
#elif defined(__GNUC__)
    /*
    I've had a bug report where GCC is emitting warnings about functions possibl
y not being inlineable. This warning happens when
    the __attribute__((always_inline)) attribute is defined without an "inline"
statement. I think therefore there must be some
    case where "__inline__" is not always defined, thus the compiler emitting th
ese warnings. When using -std=c89 or -ansi on the
    command line, we cannot use the "inline" keyword and instead need to use "__
inline__". In an attempt to work around this issue
    I am using "__inline__" only when we're compiling in strict ANSI mode.
    */
    #if defined(__STRICT_ANSI__)
        #define DRWAV_GNUC_INLINE_HINT __inline__
    #else
        #define DRWAV_GNUC_INLINE_HINT inline
    #endif

    #if (__GNUC__ > 3 || (__GNUC__ == 3 && __GNUC_MINOR__ >= 2)) || defined(__cl
ang__)
        #define DRWAV_INLINE DRWAV_GNUC_INLINE_HINT __attribute__((always_inline
))
    #else
        #define DRWAV_INLINE DRWAV_GNUC_INLINE_HINT
    #endif
#elif defined(__WATCOMC__)
    #define DRWAV_INLINE __inline
#else
    #define DRWAV_INLINE
#endif
/* End Inline */

/* SIZE_MAX */
#if defined(SIZE_MAX)
    #define DRWAV_SIZE_MAX  SIZE_MAX
#else
    #if defined(_WIN64) || defined(_LP64) || defined(__LP64__)
        #define DRWAV_SIZE_MAX  ((drwav_uint64)0xFFFFFFFFFFFFFFFF)
    #else
        #define DRWAV_SIZE_MAX  0xFFFFFFFF
    #endif
#endif
/* End SIZE_MAX */

/* Weird bit manipulation is for C89 compatibility (no direct support for 64-bit
 integers). */
#define DRWAV_INT64_MIN ((drwav_int64) ((drwav_uint64)0x80000000 << 32))
#define DRWAV_INT64_MAX ((drwav_int64)(((drwav_uint64)0x7FFFFFFF << 32) | 0xFFFF
FFFF))

#if defined(_MSC_VER) && _MSC_VER >= 1400
    #define DRWAV_HAS_BYTESWAP16_INTRINSIC
    #define DRWAV_HAS_BYTESWAP32_INTRINSIC
    #define DRWAV_HAS_BYTESWAP64_INTRINSIC
#elif defined(__clang__)
    #if defined(__has_builtin)
        #if __has_builtin(__builtin_bswap16)
            #define DRWAV_HAS_BYTESWAP16_INTRINSIC
        #endif
        #if __has_builtin(__builtin_bswap32)
            #define DRWAV_HAS_BYTESWAP32_INTRINSIC
        #endif
        #if __has_builtin(__builtin_bswap64)
            #define DRWAV_HAS_BYTESWAP64_INTRINSIC
        #endif
    #endif
#elif defined(__GNUC__)
    #if ((__GNUC__ > 4) || (__GNUC__ == 4 && __GNUC_MINOR__ >= 3))
        #define DRWAV_HAS_BYTESWAP32_INTRINSIC
        #define DRWAV_HAS_BYTESWAP64_INTRINSIC
    #endif
    #if ((__GNUC__ > 4) || (__GNUC__ == 4 && __GNUC_MINOR__ >= 8))
        #define DRWAV_HAS_BYTESWAP16_INTRINSIC
    #endif
#endif

DRWAV_API void drwav_version(drwav_uint32* pMajor, drwav_uint32* pMinor, drwav_u
int32* pRevision)
{
    if (pMajor) {
        *pMajor = DRWAV_VERSION_MAJOR;
    }

    if (pMinor) {
        *pMinor = DRWAV_VERSION_MINOR;
    }

    if (pRevision) {
        *pRevision = DRWAV_VERSION_REVISION;
    }
}

DRWAV_API const char* drwav_version_string(void)
{
    return DRWAV_VERSION_STRING;
}

/*
These limits are used for basic validation when initializing the decoder. If you
 exceed these limits, first of all: what on Earth are
you doing?! (Let me know, I'd be curious!) Second, you can adjust these by #defi
ne-ing them before the dr_wav implementation.
*/
#ifndef DRWAV_MAX_SAMPLE_RATE
#define DRWAV_MAX_SAMPLE_RATE       384000
#endif
#ifndef DRWAV_MAX_CHANNELS
#define DRWAV_MAX_CHANNELS          256
#endif
#ifndef DRWAV_MAX_BITS_PER_SAMPLE
#define DRWAV_MAX_BITS_PER_SAMPLE   64
#endif

static const drwav_uint8 drwavGUID_W64_RIFF[16] = {0x72,0x69,0x66,0x66, 0x2E,0x9
1, 0xCF,0x11, 0xA5,0xD6, 0x28,0xDB,0x04,0xC1,0x00,0x00};    /* 66666972-912E-11C
F-A5D6-28DB04C10000 */
static const drwav_uint8 drwavGUID_W64_WAVE[16] = {0x77,0x61,0x76,0x65, 0xF3,0xA
C, 0xD3,0x11, 0x8C,0xD1, 0x00,0xC0,0x4F,0x8E,0xDB,0x8A};    /* 65766177-ACF3-11D
3-8CD1-00C04F8EDB8A */
/*static const drwav_uint8 drwavGUID_W64_JUNK[16] = {0x6A,0x75,0x6E,0x6B, 0xF3,0
xAC, 0xD3,0x11, 0x8C,0xD1, 0x00,0xC0,0x4F,0x8E,0xDB,0x8A};*/    /* 6B6E756A-ACF3
-11D3-8CD1-00C04F8EDB8A */
static const drwav_uint8 drwavGUID_W64_FMT [16] = {0x66,0x6D,0x74,0x20, 0xF3,0xA
C, 0xD3,0x11, 0x8C,0xD1, 0x00,0xC0,0x4F,0x8E,0xDB,0x8A};    /* 20746D66-ACF3-11D
3-8CD1-00C04F8EDB8A */
static const drwav_uint8 drwavGUID_W64_FACT[16] = {0x66,0x61,0x63,0x74, 0xF3,0xA
C, 0xD3,0x11, 0x8C,0xD1, 0x00,0xC0,0x4F,0x8E,0xDB,0x8A};    /* 74636166-ACF3-11D
3-8CD1-00C04F8EDB8A */
static const drwav_uint8 drwavGUID_W64_DATA[16] = {0x64,0x61,0x74,0x61, 0xF3,0xA
C, 0xD3,0x11, 0x8C,0xD1, 0x00,0xC0,0x4F,0x8E,0xDB,0x8A};    /* 61746164-ACF3-11D
3-8CD1-00C04F8EDB8A */
/*static const drwav_uint8 drwavGUID_W64_SMPL[16] = {0x73,0x6D,0x70,0x6C, 0xF3,0
xAC, 0xD3,0x11, 0x8C,0xD1, 0x00,0xC0,0x4F,0x8E,0xDB,0x8A};*/    /* 6C706D73-ACF3
-11D3-8CD1-00C04F8EDB8A */


static DRWAV_INLINE int drwav__is_little_endian(void)
{
#if defined(DRWAV_X86) || defined(DRWAV_X64)
    return DRWAV_TRUE;
#elif defined(__BYTE_ORDER) && defined(__LITTLE_ENDIAN) && __BYTE_ORDER == __LIT
TLE_ENDIAN
    return DRWAV_TRUE;
#else
    int n = 1;
    return (*(char*)&n) == 1;
#endif
}


static DRWAV_INLINE void drwav_bytes_to_guid(const drwav_uint8* data, drwav_uint
8* guid)
{
    int i;
    for (i = 0; i < 16; ++i) {
        guid[i] = data[i];
    }
}


static DRWAV_INLINE drwav_uint16 drwav__bswap16(drwav_uint16 n)
{
#ifdef DRWAV_HAS_BYTESWAP16_INTRINSIC
    #if defined(_MSC_VER)
        return _byteswap_ushort(n);
    #elif defined(__GNUC__) || defined(__clang__)
        return __builtin_bswap16(n);
    #else
        #error "This compiler does not support the byte swap intrinsic."
    #endif
#else
    return ((n & 0xFF00) >> 8) |
           ((n & 0x00FF) << 8);
#endif
}

static DRWAV_INLINE drwav_uint32 drwav__bswap32(drwav_uint32 n)
{
#ifdef DRWAV_HAS_BYTESWAP32_INTRINSIC
    #if defined(_MSC_VER)
        return _byteswap_ulong(n);
    #elif defined(__GNUC__) || defined(__clang__)
        #if defined(DRWAV_ARM) && (defined(__ARM_ARCH) && __ARM_ARCH >= 6) && !d
efined(DRWAV_64BIT)   /* <-- 64-bit inline assembly has not been tested, so disa
bling for now. */
            /* Inline assembly optimized implementation for ARM. In my testing,
GCC does not generate optimized code with __builtin_bswap32(). */
            drwav_uint32 r;
            __asm__ __volatile__ (
            #if defined(DRWAV_64BIT)
                "rev %w[out], %w[in]" : [out]"=r"(r) : [in]"r"(n)   /* <-- This
is untested. If someone in the community could test this, that would be apprecia
ted! */
            #else
                "rev %[out], %[in]" : [out]"=r"(r) : [in]"r"(n)
            #endif
            );
            return r;
        #else
            return __builtin_bswap32(n);
        #endif
    #else
        #error "This compiler does not support the byte swap intrinsic."
    #endif
#else
    return ((n & 0xFF000000) >> 24) |
           ((n & 0x00FF0000) >>  8) |
           ((n & 0x0000FF00) <<  8) |
           ((n & 0x000000FF) << 24);
#endif
}

static DRWAV_INLINE drwav_uint64 drwav__bswap64(drwav_uint64 n)
{
#ifdef DRWAV_HAS_BYTESWAP64_INTRINSIC
    #if defined(_MSC_VER)
        return _byteswap_uint64(n);
    #elif defined(__GNUC__) || defined(__clang__)
        return __builtin_bswap64(n);
    #else
        #error "This compiler does not support the byte swap intrinsic."
    #endif
#else
    /* Weird "<< 32" bitshift is required for C89 because it doesn't support 64-
bit constants. Should be optimized out by a good compiler. */
    return ((n & ((drwav_uint64)0xFF000000 << 32)) >> 56) |
           ((n & ((drwav_uint64)0x00FF0000 << 32)) >> 40) |
           ((n & ((drwav_uint64)0x0000FF00 << 32)) >> 24) |
           ((n & ((drwav_uint64)0x000000FF << 32)) >>  8) |
           ((n & ((drwav_uint64)0xFF000000      )) <<  8) |
           ((n & ((drwav_uint64)0x00FF0000      )) << 24) |
           ((n & ((drwav_uint64)0x0000FF00      )) << 40) |
           ((n & ((drwav_uint64)0x000000FF      )) << 56);
#endif
}


static DRWAV_INLINE drwav_int16 drwav__bswap_s16(drwav_int16 n)
{
    return (drwav_int16)drwav__bswap16((drwav_uint16)n);
}

static DRWAV_INLINE void drwav__bswap_samples_s16(drwav_int16* pSamples, drwav_u
int64 sampleCount)
{
    drwav_uint64 iSample;
    for (iSample = 0; iSample < sampleCount; iSample += 1) {
        pSamples[iSample] = drwav__bswap_s16(pSamples[iSample]);
    }
}


static DRWAV_INLINE void drwav__bswap_s24(drwav_uint8* p)
{
    drwav_uint8 t;
    t = p[0];
    p[0] = p[2];
    p[2] = t;
}

static DRWAV_INLINE void drwav__bswap_samples_s24(drwav_uint8* pSamples, drwav_u
int64 sampleCount)
{
    drwav_uint64 iSample;
    for (iSample = 0; iSample < sampleCount; iSample += 1) {
        drwav_uint8* pSample = pSamples + (iSample*3);
        drwav__bswap_s24(pSample);
    }
}


static DRWAV_INLINE drwav_int32 drwav__bswap_s32(drwav_int32 n)
{
    return (drwav_int32)drwav__bswap32((drwav_uint32)n);
}

static DRWAV_INLINE void drwav__bswap_samples_s32(drwav_int32* pSamples, drwav_u
int64 sampleCount)
{
    drwav_uint64 iSample;
    for (iSample = 0; iSample < sampleCount; iSample += 1) {
        pSamples[iSample] = drwav__bswap_s32(pSamples[iSample]);
    }
}


static DRWAV_INLINE drwav_int64 drwav__bswap_s64(drwav_int64 n)
{
    return (drwav_int64)drwav__bswap64((drwav_uint64)n);
}

static DRWAV_INLINE void drwav__bswap_samples_s64(drwav_int64* pSamples, drwav_u
int64 sampleCount)
{
    drwav_uint64 iSample;
    for (iSample = 0; iSample < sampleCount; iSample += 1) {
        pSamples[iSample] = drwav__bswap_s64(pSamples[iSample]);
    }
}


static DRWAV_INLINE float drwav__bswap_f32(float n)
{
    union {
        drwav_uint32 i;
        float f;
    } x;
    x.f = n;
    x.i = drwav__bswap32(x.i);

    return x.f;
}

static DRWAV_INLINE void drwav__bswap_samples_f32(float* pSamples, drwav_uint64
sampleCount)
{
    drwav_uint64 iSample;
    for (iSample = 0; iSample < sampleCount; iSample += 1) {
        pSamples[iSample] = drwav__bswap_f32(pSamples[iSample]);
    }
}


static DRWAV_INLINE void drwav__bswap_samples(void* pSamples, drwav_uint64 sampl
eCount, drwav_uint32 bytesPerSample)
{
    switch (bytesPerSample)
    {
        case 1:
        {
            /* No-op. */
        } break;
        case 2:
        {
            drwav__bswap_samples_s16((drwav_int16*)pSamples, sampleCount);
        } break;
        case 3:
        {
            drwav__bswap_samples_s24((drwav_uint8*)pSamples, sampleCount);
        } break;
        case 4:
        {
            drwav__bswap_samples_s32((drwav_int32*)pSamples, sampleCount);
        } break;
        case 8:
        {
            drwav__bswap_samples_s64((drwav_int64*)pSamples, sampleCount);
        } break;
        default:
        {
            /* Unsupported format. */
            DRWAV_ASSERT(DRWAV_FALSE);
        } break;
    }
}



DRWAV_PRIVATE DRWAV_INLINE drwav_bool32 drwav_is_container_be(drwav_container co
ntainer)
{
    if (container == drwav_container_rifx || container == drwav_container_aiff)
{
        return DRWAV_TRUE;
    } else {
        return DRWAV_FALSE;
    }
}


DRWAV_PRIVATE DRWAV_INLINE drwav_uint16 drwav_bytes_to_u16_le(const drwav_uint8*
 data)
{
    return ((drwav_uint16)data[0] << 0) | ((drwav_uint16)data[1] << 8);
}

DRWAV_PRIVATE DRWAV_INLINE drwav_uint16 drwav_bytes_to_u16_be(const drwav_uint8*
 data)
{
    return ((drwav_uint16)data[1] << 0) | ((drwav_uint16)data[0] << 8);
}

DRWAV_PRIVATE DRWAV_INLINE drwav_uint16 drwav_bytes_to_u16_ex(const drwav_uint8*
 data, drwav_container container)
{
    if (drwav_is_container_be(container)) {
        return drwav_bytes_to_u16_be(data);
    } else {
        return drwav_bytes_to_u16_le(data);
    }
}


DRWAV_PRIVATE DRWAV_INLINE drwav_uint32 drwav_bytes_to_u32_le(const drwav_uint8*
 data)
{
    return ((drwav_uint32)data[0] << 0) | ((drwav_uint32)data[1] << 8) | ((drwav
_uint32)data[2] << 16) | ((drwav_uint32)data[3] << 24);
}

DRWAV_PRIVATE DRWAV_INLINE drwav_uint32 drwav_bytes_to_u32_be(const drwav_uint8*
 data)
{
    return ((drwav_uint32)data[3] << 0) | ((drwav_uint32)data[2] << 8) | ((drwav
_uint32)data[1] << 16) | ((drwav_uint32)data[0] << 24);
}

DRWAV_PRIVATE DRWAV_INLINE drwav_uint32 drwav_bytes_to_u32_ex(const drwav_uint8*
 data, drwav_container container)
{
    if (drwav_is_container_be(container)) {
        return drwav_bytes_to_u32_be(data);
    } else {
        return drwav_bytes_to_u32_le(data);
    }
}



DRWAV_PRIVATE drwav_int64 drwav_aiff_extented_to_s64(const drwav_uint8* data)
{
    drwav_uint32 exponent = ((drwav_uint32)data[0] << 8) | data[1];
    drwav_uint64 hi = ((drwav_uint64)data[2] << 24) | ((drwav_uint64)data[3] <<
16) | ((drwav_uint64)data[4] <<  8) | ((drwav_uint64)data[5] <<  0);
    drwav_uint64 lo = ((drwav_uint64)data[6] << 24) | ((drwav_uint64)data[7] <<
16) | ((drwav_uint64)data[8] <<  8) | ((drwav_uint64)data[9] <<  0);
    drwav_uint64 significand = (hi << 32) | lo;
    int sign = exponent >> 15;

    /* Remove sign bit. */
    exponent &= 0x7FFF;

    /* Special cases. */
    if (exponent == 0 && significand == 0) {
        return 0;
    } else if (exponent == 0x7FFF) {
        return sign ? DRWAV_INT64_MIN : DRWAV_INT64_MAX;    /* Infinite. */
    }

    exponent -= 16383;

    if (exponent > 63) {
        return sign ? DRWAV_INT64_MIN : DRWAV_INT64_MAX;    /* Too big for a 64-
bit integer. */
    } else if (exponent < 1) {
        return 0;  /* Number is less than 1, so rounds down to 0. */
    }

    significand >>= (63 - exponent);

    if (sign) {
        return -(drwav_int64)significand;
    } else {
        return  (drwav_int64)significand;
    }
}


DRWAV_PRIVATE void* drwav__malloc_default(size_t sz, void* pUserData)
{
    (void)pUserData;
    return DRWAV_MALLOC(sz);
}

DRWAV_PRIVATE void* drwav__realloc_default(void* p, size_t sz, void* pUserData)
{
    (void)pUserData;
    return DRWAV_REALLOC(p, sz);
}

DRWAV_PRIVATE void drwav__free_default(void* p, void* pUserData)
{
    (void)pUserData;
    DRWAV_FREE(p);
}


DRWAV_PRIVATE void* drwav__malloc_from_callbacks(size_t sz, const drwav_allocati
on_callbacks* pAllocationCallbacks)
{
    if (pAllocationCallbacks == NULL) {
        return NULL;
    }

    if (pAllocationCallbacks->onMalloc != NULL) {
        return pAllocationCallbacks->onMalloc(sz, pAllocationCallbacks->pUserDat
a);
    }

    /* Try using realloc(). */
    if (pAllocationCallbacks->onRealloc != NULL) {
        return pAllocationCallbacks->onRealloc(NULL, sz, pAllocationCallbacks->p
UserData);
    }

    return NULL;
}

DRWAV_PRIVATE void* drwav__realloc_from_callbacks(void* p, size_t szNew, size_t
szOld, const drwav_allocation_callbacks* pAllocationCallbacks)
{
    if (pAllocationCallbacks == NULL) {
        return NULL;
    }

    if (pAllocationCallbacks->onRealloc != NULL) {
        return pAllocationCallbacks->onRealloc(p, szNew, pAllocationCallbacks->p
UserData);
    }

    /* Try emulating realloc() in terms of malloc()/free(). */
    if (pAllocationCallbacks->onMalloc != NULL && pAllocationCallbacks->onFree !
= NULL) {
        void* p2;

        p2 = pAllocationCallbacks->onMalloc(szNew, pAllocationCallbacks->pUserDa
ta);
        if (p2 == NULL) {
            return NULL;
        }

        if (p != NULL) {
            DRWAV_COPY_MEMORY(p2, p, szOld);
            pAllocationCallbacks->onFree(p, pAllocationCallbacks->pUserData);
        }

        return p2;
    }

    return NULL;
}

DRWAV_PRIVATE void drwav__free_from_callbacks(void* p, const drwav_allocation_ca
llbacks* pAllocationCallbacks)
{
    if (p == NULL || pAllocationCallbacks == NULL) {
        return;
    }

    if (pAllocationCallbacks->onFree != NULL) {
        pAllocationCallbacks->onFree(p, pAllocationCallbacks->pUserData);
    }
}


DRWAV_PRIVATE drwav_allocation_callbacks drwav_copy_allocation_callbacks_or_defa
ults(const drwav_allocation_callbacks* pAllocationCallbacks)
{
    if (pAllocationCallbacks != NULL) {
        /* Copy. */
        return *pAllocationCallbacks;
    } else {
        /* Defaults. */
        drwav_allocation_callbacks allocationCallbacks;
        allocationCallbacks.pUserData = NULL;
        allocationCallbacks.onMalloc  = drwav__malloc_default;
        allocationCallbacks.onRealloc = drwav__realloc_default;
        allocationCallbacks.onFree    = drwav__free_default;
        return allocationCallbacks;
    }
}


static DRWAV_INLINE drwav_bool32 drwav__is_compressed_format_tag(drwav_uint16 fo
rmatTag)
{
    return
        formatTag == DR_WAVE_FORMAT_ADPCM ||
        formatTag == DR_WAVE_FORMAT_DVI_ADPCM;
}

DRWAV_PRIVATE unsigned int drwav__chunk_padding_size_riff(drwav_uint64 chunkSize
)
{
    return (unsigned int)(chunkSize % 2);
}

DRWAV_PRIVATE unsigned int drwav__chunk_padding_size_w64(drwav_uint64 chunkSize)
{
    return (unsigned int)(chunkSize % 8);
}

DRWAV_PRIVATE drwav_uint64 drwav_read_pcm_frames_s16__msadpcm(drwav* pWav, drwav
_uint64 samplesToRead, drwav_int16* pBufferOut);
DRWAV_PRIVATE drwav_uint64 drwav_read_pcm_frames_s16__ima(drwav* pWav, drwav_uin
t64 samplesToRead, drwav_int16* pBufferOut);
DRWAV_PRIVATE drwav_bool32 drwav_init_write__internal(drwav* pWav, const drwav_d
ata_format* pFormat, drwav_uint64 totalSampleCount);

DRWAV_PRIVATE drwav_result drwav__read_chunk_header(drwav_read_proc onRead, void
* pUserData, drwav_container container, drwav_uint64* pRunningBytesReadOut, drwa
v_chunk_header* pHeaderOut)
{
    if (container == drwav_container_riff || container == drwav_container_rifx |
| container == drwav_container_rf64 || container == drwav_container_aiff) {
        drwav_uint8 sizeInBytes[4];

        if (onRead(pUserData, pHeaderOut->id.fourcc, 4) != 4) {
            return DRWAV_AT_END;
        }

        if (onRead(pUserData, sizeInBytes, 4) != 4) {
            return DRWAV_INVALID_FILE;
        }

        pHeaderOut->sizeInBytes = drwav_bytes_to_u32_ex(sizeInBytes, container);
        pHeaderOut->paddingSize = drwav__chunk_padding_size_riff(pHeaderOut->siz
eInBytes);

        *pRunningBytesReadOut += 8;
    } else if (container == drwav_container_w64) {
        drwav_uint8 sizeInBytes[8];

        if (onRead(pUserData, pHeaderOut->id.guid, 16) != 16) {
            return DRWAV_AT_END;
        }

        if (onRead(pUserData, sizeInBytes, 8) != 8) {
            return DRWAV_INVALID_FILE;
        }

        pHeaderOut->sizeInBytes = drwav_bytes_to_u64(sizeInBytes) - 24;    /* <-
- Subtract 24 because w64 includes the size of the header. */
        pHeaderOut->paddingSize = drwav__chunk_padding_size_w64(pHeaderOut->size
InBytes);
        *pRunningBytesReadOut += 24;
    } else {
        return DRWAV_INVALID_FILE;
    }

    return DRWAV_SUCCESS;
}

DRWAV_PRIVATE drwav_bool32 drwav__seek_forward(drwav_seek_proc onSeek, drwav_uin
t64 offset, void* pUserData)
{
    drwav_uint64 bytesRemainingToSeek = offset;
    while (bytesRemainingToSeek > 0) {
        if (bytesRemainingToSeek > 0x7FFFFFFF) {
            if (!onSeek(pUserData, 0x7FFFFFFF, drwav_seek_origin_current)) {
                return DRWAV_FALSE;
            }
            bytesRemainingToSeek -= 0x7FFFFFFF;
        } else {
            if (!onSeek(pUserData, (int)bytesRemainingToSeek, drwav_seek_origin_
current)) {
                return DRWAV_FALSE;
            }
            bytesRemainingToSeek = 0;
        }
    }

    return DRWAV_TRUE;
}

DRWAV_PRIVATE drwav_bool32 drwav__seek_from_start(drwav_seek_proc onSeek, drwav_
uint64 offset, void* pUserData)
{
    if (offset <= 0x7FFFFFFF) {
        return onSeek(pUserData, (int)offset, drwav_seek_origin_start);
    }

    /* Larger than 32-bit seek. */
    if (!onSeek(pUserData, 0x7FFFFFFF, drwav_seek_origin_start)) {
        return DRWAV_FALSE;
    }
    offset -= 0x7FFFFFFF;

    for (;;) {
        if (offset <= 0x7FFFFFFF) {
            return onSeek(pUserData, (int)offset, drwav_seek_origin_current);
        }

        if (!onSeek(pUserData, 0x7FFFFFFF, drwav_seek_origin_current)) {
            return DRWAV_FALSE;
        }
        offset -= 0x7FFFFFFF;
    }

    /* Should never get here. */
    /*return DRWAV_TRUE; */
}



DRWAV_PRIVATE size_t drwav__on_read(drwav_read_proc onRead, void* pUserData, voi
d* pBufferOut, size_t bytesToRead, drwav_uint64* pCursor)
{
    size_t bytesRead;

    DRWAV_ASSERT(onRead != NULL);
    DRWAV_ASSERT(pCursor != NULL);

    bytesRead = onRead(pUserData, pBufferOut, bytesToRead);
    *pCursor += bytesRead;
    return bytesRead;
}

#if 0
DRWAV_PRIVATE drwav_bool32 drwav__on_seek(drwav_seek_proc onSeek, void* pUserDat
a, int offset, drwav_seek_origin origin, drwav_uint64* pCursor)
{
    DRWAV_ASSERT(onSeek != NULL);
    DRWAV_ASSERT(pCursor != NULL);

    if (!onSeek(pUserData, offset, origin)) {
        return DRWAV_FALSE;
    }

    if (origin == drwav_seek_origin_start) {
        *pCursor = offset;
    } else {
        *pCursor += offset;
    }

    return DRWAV_TRUE;
}
#endif


#define DRWAV_SMPL_BYTES                    36
#define DRWAV_SMPL_LOOP_BYTES               24
#define DRWAV_INST_BYTES                    7
#define DRWAV_ACID_BYTES                    24
#define DRWAV_CUE_BYTES                     4
#define DRWAV_BEXT_BYTES                    602
#define DRWAV_BEXT_DESCRIPTION_BYTES        256
#define DRWAV_BEXT_ORIGINATOR_NAME_BYTES    32
#define DRWAV_BEXT_ORIGINATOR_REF_BYTES     32
#define DRWAV_BEXT_RESERVED_BYTES           180
#define DRWAV_BEXT_UMID_BYTES               64
#define DRWAV_CUE_POINT_BYTES               24
#define DRWAV_LIST_LABEL_OR_NOTE_BYTES      4
#define DRWAV_LIST_LABELLED_TEXT_BYTES      20

#define DRWAV_METADATA_ALIGNMENT            8

typedef enum
{
    drwav__metadata_parser_stage_count,
    drwav__metadata_parser_stage_read
} drwav__metadata_parser_stage;

typedef struct
{
    drwav_read_proc onRead;
    drwav_seek_proc onSeek;
    void *pReadSeekUserData;
    drwav__metadata_parser_stage stage;
    drwav_metadata *pMetadata;
    drwav_uint32 metadataCount;
    drwav_uint8 *pData;
    drwav_uint8 *pDataCursor;
    drwav_uint64 metadataCursor;
    drwav_uint64 extraCapacity;
} drwav__metadata_parser;

DRWAV_PRIVATE size_t drwav__metadata_memory_capacity(drwav__metadata_parser* pPa
rser)
{
    drwav_uint64 cap = sizeof(drwav_metadata) * (drwav_uint64)pParser->metadataC
ount + pParser->extraCapacity;
    if (cap > DRWAV_SIZE_MAX) {
        return 0;   /* Too big. */
    }

    return (size_t)cap; /* Safe cast thanks to the check above. */
}

DRWAV_PRIVATE drwav_uint8* drwav__metadata_get_memory(drwav__metadata_parser* pP
arser, size_t size, size_t align)
{
    drwav_uint8* pResult;

    if (align) {
        drwav_uintptr modulo = (drwav_uintptr)pParser->pDataCursor % align;
        if (modulo != 0) {
            pParser->pDataCursor += align - modulo;
        }
    }

    pResult = pParser->pDataCursor;

    /*
    Getting to the point where this function is called means there should always
 be memory
    available. Out of memory checks should have been done at an earlier stage.
    */
    DRWAV_ASSERT((pResult + size) <= (pParser->pData + drwav__metadata_memory_ca
pacity(pParser)));

    pParser->pDataCursor += size;
    return pResult;
}

DRWAV_PRIVATE void drwav__metadata_request_extra_memory_for_stage_2(drwav__metad
ata_parser* pParser, size_t bytes, size_t align)
{
    size_t extra = bytes + (align ? (align - 1) : 0);
    pParser->extraCapacity += extra;
}

DRWAV_PRIVATE drwav_result drwav__metadata_alloc(drwav__metadata_parser* pParser
, drwav_allocation_callbacks* pAllocationCallbacks)
{
    if (pParser->extraCapacity != 0 || pParser->metadataCount != 0) {
        pAllocationCallbacks->onFree(pParser->pData, pAllocationCallbacks->pUser
Data);

        pParser->pData = (drwav_uint8*)pAllocationCallbacks->onMalloc(drwav__met
adata_memory_capacity(pParser), pAllocationCallbacks->pUserData);
        pParser->pDataCursor = pParser->pData;

        if (pParser->pData == NULL) {
            return DRWAV_OUT_OF_MEMORY;
        }

        /*
        We don't need to worry about specifying an alignment here because malloc
 always returns something
        of suitable alignment. This also means pParser->pMetadata is all that we
 need to store in order
        for us to free when we are done.
        */
        pParser->pMetadata = (drwav_metadata*)drwav__metadata_get_memory(pParser
, sizeof(drwav_metadata) * pParser->metadataCount, 1);
        pParser->metadataCursor = 0;
    }

    return DRWAV_SUCCESS;
}

DRWAV_PRIVATE size_t drwav__metadata_parser_read(drwav__metadata_parser* pParser
, void* pBufferOut, size_t bytesToRead, drwav_uint64* pCursor)
{
    if (pCursor != NULL) {
        return drwav__on_read(pParser->onRead, pParser->pReadSeekUserData, pBuff
erOut, bytesToRead, pCursor);
    } else {
        return pParser->onRead(pParser->pReadSeekUserData, pBufferOut, bytesToRe
ad);
    }
}

DRWAV_PRIVATE drwav_uint64 drwav__read_smpl_to_metadata_obj(drwav__metadata_pars
er* pParser, const drwav_chunk_header* pChunkHeader, drwav_metadata* pMetadata)
{
    drwav_uint8 smplHeaderData[DRWAV_SMPL_BYTES];
    drwav_uint64 totalBytesRead = 0;
    size_t bytesJustRead;

    if (pMetadata == NULL) {
        return 0;
    }

    bytesJustRead = drwav__metadata_parser_read(pParser, smplHeaderData, sizeof(
smplHeaderData), &totalBytesRead);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);
    DRWAV_ASSERT(pChunkHeader != NULL);

    if (pMetadata != NULL && bytesJustRead == sizeof(smplHeaderData)) {
        drwav_uint32 iSampleLoop;

        pMetadata->type                                     = drwav_metadata_typ
e_smpl;
        pMetadata->data.smpl.manufacturerId                 = drwav_bytes_to_u32
(smplHeaderData + 0);
        pMetadata->data.smpl.productId                      = drwav_bytes_to_u32
(smplHeaderData + 4);
        pMetadata->data.smpl.samplePeriodNanoseconds        = drwav_bytes_to_u32
(smplHeaderData + 8);
        pMetadata->data.smpl.midiUnityNote                  = drwav_bytes_to_u32
(smplHeaderData + 12);
        pMetadata->data.smpl.midiPitchFraction              = drwav_bytes_to_u32
(smplHeaderData + 16);
        pMetadata->data.smpl.smpteFormat                    = drwav_bytes_to_u32
(smplHeaderData + 20);
        pMetadata->data.smpl.smpteOffset                    = drwav_bytes_to_u32
(smplHeaderData + 24);
        pMetadata->data.smpl.sampleLoopCount                = drwav_bytes_to_u32
(smplHeaderData + 28);
        pMetadata->data.smpl.samplerSpecificDataSizeInBytes = drwav_bytes_to_u32
(smplHeaderData + 32);

        /*
        The loop count needs to be validated against the size of the chunk for s
afety so we don't
        attempt to read over the boundary of the chunk.
        */
        if (pMetadata->data.smpl.sampleLoopCount == (pChunkHeader->sizeInBytes -
 DRWAV_SMPL_BYTES) / DRWAV_SMPL_LOOP_BYTES) {
            pMetadata->data.smpl.pLoops = (drwav_smpl_loop*)drwav__metadata_get_
memory(pParser, sizeof(drwav_smpl_loop) * pMetadata->data.smpl.sampleLoopCount,
DRWAV_METADATA_ALIGNMENT);

            for (iSampleLoop = 0; iSampleLoop < pMetadata->data.smpl.sampleLoopC
ount; ++iSampleLoop) {
                drwav_uint8 smplLoopData[DRWAV_SMPL_LOOP_BYTES];
                bytesJustRead = drwav__metadata_parser_read(pParser, smplLoopDat
a, sizeof(smplLoopData), &totalBytesRead);

                if (bytesJustRead == sizeof(smplLoopData)) {
                    pMetadata->data.smpl.pLoops[iSampleLoop].cuePointId        =
 drwav_bytes_to_u32(smplLoopData + 0);
                    pMetadata->data.smpl.pLoops[iSampleLoop].type              =
 drwav_bytes_to_u32(smplLoopData + 4);
                    pMetadata->data.smpl.pLoops[iSampleLoop].firstSampleOffset =
 drwav_bytes_to_u32(smplLoopData + 8);
                    pMetadata->data.smpl.pLoops[iSampleLoop].lastSampleOffset  =
 drwav_bytes_to_u32(smplLoopData + 12);
                    pMetadata->data.smpl.pLoops[iSampleLoop].sampleFraction    =
 drwav_bytes_to_u32(smplLoopData + 16);
                    pMetadata->data.smpl.pLoops[iSampleLoop].playCount         =
 drwav_bytes_to_u32(smplLoopData + 20);
                } else {
                    break;
                }
            }

            if (pMetadata->data.smpl.samplerSpecificDataSizeInBytes > 0) {
                pMetadata->data.smpl.pSamplerSpecificData = drwav__metadata_get_
memory(pParser, pMetadata->data.smpl.samplerSpecificDataSizeInBytes, 1);
                DRWAV_ASSERT(pMetadata->data.smpl.pSamplerSpecificData != NULL);

                drwav__metadata_parser_read(pParser, pMetadata->data.smpl.pSampl
erSpecificData, pMetadata->data.smpl.samplerSpecificDataSizeInBytes, &totalBytes
Read);
            }
        }
    }

    return totalBytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__read_cue_to_metadata_obj(drwav__metadata_parse
r* pParser, const drwav_chunk_header* pChunkHeader, drwav_metadata* pMetadata)
{
    drwav_uint8 cueHeaderSectionData[DRWAV_CUE_BYTES];
    drwav_uint64 totalBytesRead = 0;
    size_t bytesJustRead;

    if (pMetadata == NULL) {
        return 0;
    }

    bytesJustRead = drwav__metadata_parser_read(pParser, cueHeaderSectionData, s
izeof(cueHeaderSectionData), &totalBytesRead);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);

    if (bytesJustRead == sizeof(cueHeaderSectionData)) {
        pMetadata->type                   = drwav_metadata_type_cue;
        pMetadata->data.cue.cuePointCount = drwav_bytes_to_u32(cueHeaderSectionD
ata);

        /*
        We need to validate the cue point count against the size of the chunk so
 we don't read
        beyond the chunk.
        */
        if (pMetadata->data.cue.cuePointCount == (pChunkHeader->sizeInBytes - DR
WAV_CUE_BYTES) / DRWAV_CUE_POINT_BYTES) {
            pMetadata->data.cue.pCuePoints    = (drwav_cue_point*)drwav__metadat
a_get_memory(pParser, sizeof(drwav_cue_point) * pMetadata->data.cue.cuePointCoun
t, DRWAV_METADATA_ALIGNMENT);
            DRWAV_ASSERT(pMetadata->data.cue.pCuePoints != NULL);

            if (pMetadata->data.cue.cuePointCount > 0) {
                drwav_uint32 iCuePoint;

                for (iCuePoint = 0; iCuePoint < pMetadata->data.cue.cuePointCoun
t; ++iCuePoint) {
                    drwav_uint8 cuePointData[DRWAV_CUE_POINT_BYTES];
                    bytesJustRead = drwav__metadata_parser_read(pParser, cuePoin
tData, sizeof(cuePointData), &totalBytesRead);

                    if (bytesJustRead == sizeof(cuePointData)) {
                        pMetadata->data.cue.pCuePoints[iCuePoint].id
    = drwav_bytes_to_u32(cuePointData + 0);
                        pMetadata->data.cue.pCuePoints[iCuePoint].playOrderPosit
ion = drwav_bytes_to_u32(cuePointData + 4);
                        pMetadata->data.cue.pCuePoints[iCuePoint].dataChunkId[0]
    = cuePointData[8];
                        pMetadata->data.cue.pCuePoints[iCuePoint].dataChunkId[1]
    = cuePointData[9];
                        pMetadata->data.cue.pCuePoints[iCuePoint].dataChunkId[2]
    = cuePointData[10];
                        pMetadata->data.cue.pCuePoints[iCuePoint].dataChunkId[3]
    = cuePointData[11];
                        pMetadata->data.cue.pCuePoints[iCuePoint].chunkStart
    = drwav_bytes_to_u32(cuePointData + 12);
                        pMetadata->data.cue.pCuePoints[iCuePoint].blockStart
    = drwav_bytes_to_u32(cuePointData + 16);
                        pMetadata->data.cue.pCuePoints[iCuePoint].sampleOffset
    = drwav_bytes_to_u32(cuePointData + 20);
                    } else {
                        break;
                    }
                }
            }
        }
    }

    return totalBytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__read_inst_to_metadata_obj(drwav__metadata_pars
er* pParser, drwav_metadata* pMetadata)
{
    drwav_uint8 instData[DRWAV_INST_BYTES];
    drwav_uint64 bytesRead;

    if (pMetadata == NULL) {
        return 0;
    }

    bytesRead = drwav__metadata_parser_read(pParser, instData, sizeof(instData),
 NULL);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);

    if (bytesRead == sizeof(instData)) {
        pMetadata->type                    = drwav_metadata_type_inst;
        pMetadata->data.inst.midiUnityNote = (drwav_int8)instData[0];
        pMetadata->data.inst.fineTuneCents = (drwav_int8)instData[1];
        pMetadata->data.inst.gainDecibels  = (drwav_int8)instData[2];
        pMetadata->data.inst.lowNote       = (drwav_int8)instData[3];
        pMetadata->data.inst.highNote      = (drwav_int8)instData[4];
        pMetadata->data.inst.lowVelocity   = (drwav_int8)instData[5];
        pMetadata->data.inst.highVelocity  = (drwav_int8)instData[6];
    }

    return bytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__read_acid_to_metadata_obj(drwav__metadata_pars
er* pParser, drwav_metadata* pMetadata)
{
    drwav_uint8 acidData[DRWAV_ACID_BYTES];
    drwav_uint64 bytesRead;

    if (pMetadata == NULL) {
        return 0;
    }

    bytesRead = drwav__metadata_parser_read(pParser, acidData, sizeof(acidData),
 NULL);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);

    if (bytesRead == sizeof(acidData)) {
        pMetadata->type                       = drwav_metadata_type_acid;
        pMetadata->data.acid.flags            = drwav_bytes_to_u32(acidData + 0)
;
        pMetadata->data.acid.midiUnityNote    = drwav_bytes_to_u16(acidData + 4)
;
        pMetadata->data.acid.reserved1        = drwav_bytes_to_u16(acidData + 6)
;
        pMetadata->data.acid.reserved2        = drwav_bytes_to_f32(acidData + 8)
;
        pMetadata->data.acid.numBeats         = drwav_bytes_to_u32(acidData + 12
);
        pMetadata->data.acid.meterDenominator = drwav_bytes_to_u16(acidData + 16
);
        pMetadata->data.acid.meterNumerator   = drwav_bytes_to_u16(acidData + 18
);
        pMetadata->data.acid.tempo            = drwav_bytes_to_f32(acidData + 20
);
    }

    return bytesRead;
}

DRWAV_PRIVATE size_t drwav__strlen(const char* str)
{
    size_t result = 0;

    while (*str++) {
        result += 1;
    }

    return result;
}

DRWAV_PRIVATE size_t drwav__strlen_clamped(const char* str, size_t maxToRead)
{
    size_t result = 0;

    while (*str++ && result < maxToRead) {
        result += 1;
    }

    return result;
}

DRWAV_PRIVATE char* drwav__metadata_copy_string(drwav__metadata_parser* pParser,
 const char* str, size_t maxToRead)
{
    size_t len = drwav__strlen_clamped(str, maxToRead);

    if (len) {
        char* result = (char*)drwav__metadata_get_memory(pParser, len + 1, 1);
        DRWAV_ASSERT(result != NULL);

        DRWAV_COPY_MEMORY(result, str, len);
        result[len] = '\0';

        return result;
    } else {
        return NULL;
    }
}

typedef struct
{
    const void* pBuffer;
    size_t sizeInBytes;
    size_t cursor;
} drwav_buffer_reader;

DRWAV_PRIVATE drwav_result drwav_buffer_reader_init(const void* pBuffer, size_t
sizeInBytes, drwav_buffer_reader* pReader)
{
    DRWAV_ASSERT(pBuffer != NULL);
    DRWAV_ASSERT(pReader != NULL);

    DRWAV_ZERO_OBJECT(pReader);

    pReader->pBuffer     = pBuffer;
    pReader->sizeInBytes = sizeInBytes;
    pReader->cursor      = 0;

    return DRWAV_SUCCESS;
}

DRWAV_PRIVATE const void* drwav_buffer_reader_ptr(const drwav_buffer_reader* pRe
ader)
{
    DRWAV_ASSERT(pReader != NULL);

    return drwav_offset_ptr(pReader->pBuffer, pReader->cursor);
}

DRWAV_PRIVATE drwav_result drwav_buffer_reader_seek(drwav_buffer_reader* pReader
, size_t bytesToSeek)
{
    DRWAV_ASSERT(pReader != NULL);

    if (pReader->cursor + bytesToSeek > pReader->sizeInBytes) {
        return DRWAV_BAD_SEEK;  /* Seeking too far forward. */
    }

    pReader->cursor += bytesToSeek;

    return DRWAV_SUCCESS;
}

DRWAV_PRIVATE drwav_result drwav_buffer_reader_read(drwav_buffer_reader* pReader
, void* pDst, size_t bytesToRead, size_t* pBytesRead)
{
    drwav_result result = DRWAV_SUCCESS;
    size_t bytesRemaining;

    DRWAV_ASSERT(pReader != NULL);

    if (pBytesRead != NULL) {
        *pBytesRead = 0;
    }

    bytesRemaining = (pReader->sizeInBytes - pReader->cursor);
    if (bytesToRead > bytesRemaining) {
        bytesToRead = bytesRemaining;
    }

    if (pDst == NULL) {
        /* Seek. */
        result = drwav_buffer_reader_seek(pReader, bytesToRead);
    } else {
        /* Read. */
        DRWAV_COPY_MEMORY(pDst, drwav_buffer_reader_ptr(pReader), bytesToRead);
        pReader->cursor += bytesToRead;
    }

    DRWAV_ASSERT(pReader->cursor <= pReader->sizeInBytes);

    if (result == DRWAV_SUCCESS) {
        if (pBytesRead != NULL) {
            *pBytesRead = bytesToRead;
        }
    }

    return DRWAV_SUCCESS;
}

DRWAV_PRIVATE drwav_result drwav_buffer_reader_read_u16(drwav_buffer_reader* pRe
ader, drwav_uint16* pDst)
{
    drwav_result result;
    size_t bytesRead;
    drwav_uint8 data[2];

    DRWAV_ASSERT(pReader != NULL);
    DRWAV_ASSERT(pDst != NULL);

    *pDst = 0;  /* Safety. */

    result = drwav_buffer_reader_read(pReader, data, sizeof(*pDst), &bytesRead);
    if (result != DRWAV_SUCCESS || bytesRead != sizeof(*pDst)) {
        return result;
    }

    *pDst = drwav_bytes_to_u16(data);

    return DRWAV_SUCCESS;
}

DRWAV_PRIVATE drwav_result drwav_buffer_reader_read_u32(drwav_buffer_reader* pRe
ader, drwav_uint32* pDst)
{
    drwav_result result;
    size_t bytesRead;
    drwav_uint8 data[4];

    DRWAV_ASSERT(pReader != NULL);
    DRWAV_ASSERT(pDst != NULL);

    *pDst = 0;  /* Safety. */

    result = drwav_buffer_reader_read(pReader, data, sizeof(*pDst), &bytesRead);
    if (result != DRWAV_SUCCESS || bytesRead != sizeof(*pDst)) {
        return result;
    }

    *pDst = drwav_bytes_to_u32(data);

    return DRWAV_SUCCESS;
}



DRWAV_PRIVATE drwav_uint64 drwav__read_bext_to_metadata_obj(drwav__metadata_pars
er* pParser, drwav_metadata* pMetadata, drwav_uint64 chunkSize)
{
    drwav_uint8 bextData[DRWAV_BEXT_BYTES];
    size_t bytesRead = drwav__metadata_parser_read(pParser, bextData, sizeof(bex
tData), NULL);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);

    if (bytesRead == sizeof(bextData)) {
        drwav_buffer_reader reader;
        drwav_uint32 timeReferenceLow;
        drwav_uint32 timeReferenceHigh;
        size_t extraBytes;

        pMetadata->type = drwav_metadata_type_bext;

        if (drwav_buffer_reader_init(bextData, bytesRead, &reader) == DRWAV_SUCC
ESS) {
            pMetadata->data.bext.pDescription = drwav__metadata_copy_string(pPar
ser, (const char*)drwav_buffer_reader_ptr(&reader), DRWAV_BEXT_DESCRIPTION_BYTES
);
            drwav_buffer_reader_seek(&reader, DRWAV_BEXT_DESCRIPTION_BYTES);

            pMetadata->data.bext.pOriginatorName = drwav__metadata_copy_string(p
Parser, (const char*)drwav_buffer_reader_ptr(&reader), DRWAV_BEXT_ORIGINATOR_NAM
E_BYTES);
            drwav_buffer_reader_seek(&reader, DRWAV_BEXT_ORIGINATOR_NAME_BYTES);

            pMetadata->data.bext.pOriginatorReference = drwav__metadata_copy_str
ing(pParser, (const char*)drwav_buffer_reader_ptr(&reader), DRWAV_BEXT_ORIGINATO
R_REF_BYTES);
            drwav_buffer_reader_seek(&reader, DRWAV_BEXT_ORIGINATOR_REF_BYTES);

            drwav_buffer_reader_read(&reader, pMetadata->data.bext.pOriginationD
ate, sizeof(pMetadata->data.bext.pOriginationDate), NULL);
            drwav_buffer_reader_read(&reader, pMetadata->data.bext.pOriginationT
ime, sizeof(pMetadata->data.bext.pOriginationTime), NULL);

            drwav_buffer_reader_read_u32(&reader, &timeReferenceLow);
            drwav_buffer_reader_read_u32(&reader, &timeReferenceHigh);
            pMetadata->data.bext.timeReference = ((drwav_uint64)timeReferenceHig
h << 32) + timeReferenceLow;

            drwav_buffer_reader_read_u16(&reader, &pMetadata->data.bext.version)
;

            pMetadata->data.bext.pUMID = drwav__metadata_get_memory(pParser, DRW
AV_BEXT_UMID_BYTES, 1);
            drwav_buffer_reader_read(&reader, pMetadata->data.bext.pUMID, DRWAV_
BEXT_UMID_BYTES, NULL);

            drwav_buffer_reader_read_u16(&reader, &pMetadata->data.bext.loudness
Value);
            drwav_buffer_reader_read_u16(&reader, &pMetadata->data.bext.loudness
Range);
            drwav_buffer_reader_read_u16(&reader, &pMetadata->data.bext.maxTrueP
eakLevel);
            drwav_buffer_reader_read_u16(&reader, &pMetadata->data.bext.maxMomen
taryLoudness);
            drwav_buffer_reader_read_u16(&reader, &pMetadata->data.bext.maxShort
TermLoudness);

            DRWAV_ASSERT((drwav_offset_ptr(drwav_buffer_reader_ptr(&reader), DRW
AV_BEXT_RESERVED_BYTES)) == (bextData + DRWAV_BEXT_BYTES));

            extraBytes = (size_t)(chunkSize - DRWAV_BEXT_BYTES);
            if (extraBytes > 0) {
                pMetadata->data.bext.pCodingHistory = (char*)drwav__metadata_get
_memory(pParser, extraBytes + 1, 1);
                DRWAV_ASSERT(pMetadata->data.bext.pCodingHistory != NULL);

                bytesRead += drwav__metadata_parser_read(pParser, pMetadata->dat
a.bext.pCodingHistory, extraBytes, NULL);
                pMetadata->data.bext.codingHistorySize = (drwav_uint32)drwav__st
rlen(pMetadata->data.bext.pCodingHistory);
            } else {
                pMetadata->data.bext.pCodingHistory    = NULL;
                pMetadata->data.bext.codingHistorySize = 0;
            }
        }
    }

    return bytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__read_list_label_or_note_to_metadata_obj(drwav_
__metadata_parser* pParser, drwav_metadata* pMetadata, drwav_uint64 chunkSize, dr
wav_metadata_type type)
{
    drwav_uint8 cueIDBuffer[DRWAV_LIST_LABEL_OR_NOTE_BYTES];
    drwav_uint64 totalBytesRead = 0;
    size_t bytesJustRead = drwav__metadata_parser_read(pParser, cueIDBuffer, siz
eof(cueIDBuffer), &totalBytesRead);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);

    if (bytesJustRead == sizeof(cueIDBuffer)) {
        drwav_uint32 sizeIncludingNullTerminator;

        pMetadata->type = type;
        pMetadata->data.labelOrNote.cuePointId = drwav_bytes_to_u32(cueIDBuffer)
;

        sizeIncludingNullTerminator = (drwav_uint32)chunkSize - DRWAV_LIST_LABEL
_OR_NOTE_BYTES;
        if (sizeIncludingNullTerminator > 0) {
            pMetadata->data.labelOrNote.stringLength = sizeIncludingNullTerminat
or - 1;
            pMetadata->data.labelOrNote.pString      = (char*)drwav__metadata_ge
t_memory(pParser, sizeIncludingNullTerminator, 1);
            DRWAV_ASSERT(pMetadata->data.labelOrNote.pString != NULL);

            drwav__metadata_parser_read(pParser, pMetadata->data.labelOrNote.pSt
ring, sizeIncludingNullTerminator, &totalBytesRead);
        } else {
            pMetadata->data.labelOrNote.stringLength = 0;
            pMetadata->data.labelOrNote.pString      = NULL;
        }
    }

    return totalBytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__read_list_labelled_cue_region_to_metadata_obj(
drwav__metadata_parser* pParser, drwav_metadata* pMetadata, drwav_uint64 chunkSi
ze)
{
    drwav_uint8 buffer[DRWAV_LIST_LABELLED_TEXT_BYTES];
    drwav_uint64 totalBytesRead = 0;
    size_t bytesJustRead = drwav__metadata_parser_read(pParser, buffer, sizeof(b
uffer), &totalBytesRead);

    DRWAV_ASSERT(pParser->stage == drwav__metadata_parser_stage_read);

    if (bytesJustRead == sizeof(buffer)) {
        drwav_uint32 sizeIncludingNullTerminator;

        pMetadata->type                                = drwav_metadata_type_lis
t_labelled_cue_region;
        pMetadata->data.labelledCueRegion.cuePointId   = drwav_bytes_to_u32(buff
er + 0);
        pMetadata->data.labelledCueRegion.sampleLength = drwav_bytes_to_u32(buff
er + 4);
        pMetadata->data.labelledCueRegion.purposeId[0] = buffer[8];
        pMetadata->data.labelledCueRegion.purposeId[1] = buffer[9];
        pMetadata->data.labelledCueRegion.purposeId[2] = buffer[10];
        pMetadata->data.labelledCueRegion.purposeId[3] = buffer[11];
        pMetadata->data.labelledCueRegion.country      = drwav_bytes_to_u16(buff
er + 12);
        pMetadata->data.labelledCueRegion.language     = drwav_bytes_to_u16(buff
er + 14);
        pMetadata->data.labelledCueRegion.dialect      = drwav_bytes_to_u16(buff
er + 16);
        pMetadata->data.labelledCueRegion.codePage     = drwav_bytes_to_u16(buff
er + 18);

        sizeIncludingNullTerminator = (drwav_uint32)chunkSize - DRWAV_LIST_LABEL
LED_TEXT_BYTES;
        if (sizeIncludingNullTerminator > 0) {
            pMetadata->data.labelledCueRegion.stringLength = sizeIncludingNullTe
rminator - 1;
            pMetadata->data.labelledCueRegion.pString      = (char*)drwav__metad
ata_get_memory(pParser, sizeIncludingNullTerminator, 1);
            DRWAV_ASSERT(pMetadata->data.labelledCueRegion.pString != NULL);

            drwav__metadata_parser_read(pParser, pMetadata->data.labelledCueRegi
on.pString, sizeIncludingNullTerminator, &totalBytesRead);
        } else {
            pMetadata->data.labelledCueRegion.stringLength = 0;
            pMetadata->data.labelledCueRegion.pString      = NULL;
        }
    }

    return totalBytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__metadata_process_info_text_chunk(drwav__metada
ta_parser* pParser, drwav_uint64 chunkSize, drwav_metadata_type type)
{
    drwav_uint64 bytesRead = 0;
    drwav_uint32 stringSizeWithNullTerminator = (drwav_uint32)chunkSize;

    if (pParser->stage == drwav__metadata_parser_stage_count) {
        pParser->metadataCount += 1;
        drwav__metadata_request_extra_memory_for_stage_2(pParser, stringSizeWith
NullTerminator, 1);
    } else {
        drwav_metadata* pMetadata = &pParser->pMetadata[pParser->metadataCursor]
;
        pMetadata->type = type;
        if (stringSizeWithNullTerminator > 0) {
            pMetadata->data.infoText.stringLength = stringSizeWithNullTerminator
 - 1;
            pMetadata->data.infoText.pString = (char*)drwav__metadata_get_memory
(pParser, stringSizeWithNullTerminator, 1);
            DRWAV_ASSERT(pMetadata->data.infoText.pString != NULL);

            bytesRead = drwav__metadata_parser_read(pParser, pMetadata->data.inf
oText.pString, (size_t)stringSizeWithNullTerminator, NULL);
            if (bytesRead == chunkSize) {
                pParser->metadataCursor += 1;
            } else {
                /* Failed to parse. */
            }
        } else {
            pMetadata->data.infoText.stringLength = 0;
            pMetadata->data.infoText.pString      = NULL;
            pParser->metadataCursor += 1;
        }
    }

    return bytesRead;
}

DRWAV_PRIVATE drwav_uint64 drwav__metadata_process_unknown_chunk(drwav__metadata
_parser* pParser, const drwav_uint8* pChunkId, drwav_uint64 chunkSize, drwav_met
adata_location location)
{
    drwav_uint64 bytesRead = 0;

    if (location == drwav_metadata_location_invalid) {
        return 0;
    }

    if (drwav_fourcc_equal(pChunkId, "data") || drwav_fourcc_equal(pChunkId, "fm
t ") || drwav_fourcc_equal(pChunkId, "fact")) {
        return 0;
    }

    if (pParser->stage == drwav__metadata_parser_stage_count) {
        pParser->metadataCount += 1;
        drwav__metadata_request_extra_memory_for_stage_2(pParser, (size_t)chunkS
ize, 1);
    } else {
        drwav_metadata* pMetadata = &pParser->pMetadata[pParser->metadataCursor]
;
        pMetadata->type                         = drwav_metadata_type_unknown;
        pMetadata->data.unknown.chunkLocation   = location;
        pMetadata->data.unknown.id[0]           = pChunkId[0];
        pMetadata->data.unknown.id[1]           = pChunkId[1];
        pMetadata->data.unknown.id[2]           = pChunkId[2];
        pMetadata->data.unknown.id[3]           = pChunkId[3];
        pMetadata->data.unknown.dataSizeInBytes = (drwav_uint32)chunkSize;
        pMetadata->data.unknown.pData           = (drwav_uint8 *)drwav__metadata
_get_memory(pParser, (size_t)chunkSize, 1);
        DRWAV_ASSERT(pMetadata->data.unknown.pData != NULL);

        bytesRead = drwav__metadata_parser_read(pParser, pMetadata->data.unknown
.pData, pMetadata->data.unknown.dataSizeInBytes, NULL);
        if (bytesRead == pMetadata->data.unknown.dataSizeInBytes) {
            pParser->metadataCursor += 1;
        } else {
            /* Failed to read. */
        }
    }

    return bytesRead;
}

DRWAV_PRIVATE drwav_bool32 drwav__chunk_matches(drwav_metadata_type allowedMetad
ataTypes, const drwav_uint8* pChunkID, drwav_metadata_type type, const char* pID
)
{
    return (allowedMetadataTypes & type) && drwav_fourcc_equal(pChunkID, pID);
}

DRWAV_PRIVATE drwav_uint64 drwav__metadata_process_chunk(drwav__metadata_parser*
 pParser, const drwav_chunk_header* pChunkHeader, drwav_metadata_type allowedMet
adataTypes)
{
    const drwav_uint8 *pChunkID = pChunkHeader->id.fourcc;
    drwav_uint64 bytesRead = 0;

    if (drwav__chunk_matches(allowedMetadataTypes, pChunkID, drwav_metadata_type
_smpl, "smpl")) {
        if (pChunkHeader->sizeInBytes >= DRWAV_SMPL_BYTES) {
            if (pParser->stage == drwav__metadata_parser_stage_count) {
                drwav_uint8 buffer[4];
                size_t bytesJustRead;

                if (!pParser->onSeek(pParser->pReadSeekUserData, 28, drwav_seek_
origin_current)) {
                    return bytesRead;
                }
                bytesRead += 28;

                bytesJustRead = drwav__metadata_parser_read(pParser, buffer, siz
eof(buffer), &bytesRead);
                if (bytesJustRead == sizeof(buffer)) {
                    drwav_uint32 loopCount = drwav_bytes_to_u32(buffer);
                    drwav_uint64 calculatedLoopCount;

                    /* The loop count must be validated against the size of the
chunk. */
                    calculatedLoopCount = (pChunkHeader->sizeInBytes - DRWAV_SMP
L_BYTES) / DRWAV_SMPL_LOOP_BYTES;
                    if (calculatedLoopCount == loopCount) {
                        bytesJustRead = drwav__metadata_parser_read(pParser, buf
fer, sizeof(buffer), &bytesRead);
                        if (bytesJustRead == sizeof(buffer)) {
                            drwav_uint32 samplerSpecificDataSizeInBytes = drwav_
bytes_to_u32(buffer);

                            pParser->metadataCount += 1;
                            drwav__metadata_request_extra_memory_for_stage_2(pPa
rser, sizeof(drwav_smpl_loop) * loopCount, DRWAV_METADATA_ALIGNMENT);
                            drwav__metadata_request_extra_memory_for_stage_2(pPa
rser, samplerSpecificDataSizeInBytes, 1);
                        }
                    } else {
                        /* Loop count in header does not match the size of the c
hunk. */
                    }
                }
            } else {
                bytesRead = drwav__read_smpl_to_metadata_obj(pParser, pChunkHead
er, &pParser->pMetadata[pParser->metadataCursor]);
                if (bytesRead == pChunkHeader->sizeInBytes) {
                    pParser->metadataCursor += 1;
                } else {
                    /* Failed to parse. */
                }
            }
        } else {
            /* Incorrectly formed chunk. */
        }
    } else if (drwav__chunk_matches(allowedMetadataTypes, pChunkID, drwav_metada
ta_type_inst, "inst")) {
        if (pChunkHeader->sizeInBytes == DRWAV_INST_BYTES) {
            if (pParser->stage == drwav__metadata_parser_stage_count) {
                pParser->metadataCount += 1;
            } else {
                bytesRead = drwav__read_inst_to_metadata_obj(pParser, &pParser->
pMetadata[pParser->metadataCursor]);
                if (bytesRead == pChunkHeader->sizeInBytes) {
                    pParser->metadataCursor += 1;
                } else {
                    /* Failed to parse. */
                }
            }
        } else {
            /* Incorrectly formed chunk. */
        }
    } else if (drwav__chunk_matches(allowedMetadataTypes, pChunkID, drwav_metada
ta_type_acid, "acid")) {
        if (pChunkHeader->sizeInBytes == DRWAV_ACID_BYTES) {
            if (pParser->stage == drwav__metadata_parser_stage_count) {
                pParser->metadataCount += 1;
            } else {
                bytesRead = drwav__read_acid_to_metadata_obj(pParser, &pParser->
pMetadata[pParser->metadataCursor]);
                if (bytesRead == pChunkHeader->sizeInBytes) {
                    pParser->metadataCursor += 1;
                } else {
                    /* Failed to parse. */
                }
            }
        } else {
            /* Incorrectly formed chunk. */
        }
    } else if (drwav__chunk_matches(allowedMetadataTypes, pChunkID, drwav_metada
ta_type_cue, "cue ")) {
        if (pChunkHeader->sizeInBytes >= DRWAV_CUE_BYTES) {
            if (pParser->stage == drwav__metadata_parser_stage_count) {
                size_t cueCount;

                pParser->metadataCount += 1;
                cueCount = (size_t)(pChunkHeader->sizeInBytes - DRWAV_CUE_BYTES)
 / DRWAV_CUE_POINT_BYTES;
                drwav__metadata_request_extra_memory_for_stage_2(pParser, sizeof
(drwav_cue_point) * cueCount, DRWAV_METADATA_ALIGNMENT);
            } else {
                bytesRead = drwav__read_cue_to_metadata_obj(pParser, pChunkHeade
r, &pParser->pMetadata[pParser->metadataCursor]);
                if (bytesRead == pChunkHeader->sizeInBytes) {
                    pParser->metadataCursor += 1;
                } else {
                    /* Failed to parse. */
                }
            }
        } else {
            /* Incorrectly formed chunk. */
        }
    } else if (drwav__chunk_matches(allowedMetadataTypes, pChunkID, drwav_metada
ta_type_bext, "bext")) {
        if (pChunkHeader->sizeInBytes >= DRWAV_BEXT_BYTES) {
            if (pParser->stage == drwav__metadata_parser_stage_count) {
                /* The description field is the largest one in a bext chunk, so
that is the max size of this temporary buffer. */
                char buffer[DRWAV_BEXT_DESCRIPTION_BYTES + 1];
                size_t allocSizeNeeded = DRWAV_BEXT_UMID_BYTES; /* We know we wi
ll need SMPTE umid size. */
                size_t bytesJustRead;

                buffer[DRWAV_BEXT_DESCRIPTION_BYTES] = '\0';
                bytesJustRead = drwav__metadata_parser_read(pParser, buffer, DRW
AV_BEXT_DESCRIPTION_BYTES, &bytesRead);
                if (bytesJustRead != DRWAV_BEXT_DESCRIPTION_BYTES) {
                    return bytesRead;
                }
                allocSizeNeeded += drwav__strlen(buffer) + 1;

                buffer[DRWAV_BEXT_ORIGINATOR_NAME_BYTES] = '\0';
                bytesJustRead = drwav__metadata_parser_read(pParser, buffer, DRW
AV_BEXT_ORIGINATOR_NAME_BYTES, &bytesRead);
                if (bytesJustRead != DRWAV_BEXT_ORIGINATOR_NAME_BYTES) {
                    return bytesRead;
                }
                allocSizeNeeded += drwav__strlen(buffer) + 1;

                buffer[DRWAV_BEXT_ORIGINATOR_REF_BYTES] = '\0';
                bytesJustRead = drwav__metadata_parser_read(pParser, buffer, DRW
AV_BEXT_ORIGINATOR_REF_BYTES, &bytesRead);
                if (bytesJustRead != DRWAV_BEXT_ORIGINATOR_REF_BYTES) {
                    return bytesRead;
                }
                allocSizeNeeded += drwav__strlen(buffer) + 1;
                allocSizeNeeded += (size_t)pChunkHeader->sizeInBytes - DRWAV_BEX
T_BYTES + 1; /* Coding history. */

                drwav__metadata_request_extra_memory_for_stage_2(pParser, allocS
izeNeeded, 1);

                pParser->metadataCount += 1;
            } else {
                bytesRead = drwav__read_bext_to_metadata_obj(pParser, &pParser->
pMetadata[pParser->metadataCursor], pChunkHeader->sizeInBytes);
                if (bytesRead == pChunkHeader->sizeInBytes) {
                    pParser->metadataCursor += 1;
                } else {
                    /* Failed to parse. */
                }
            }
        } else {
            /* Incorrectly formed chunk. */
        }
    } else if (drwav_fourcc_equal(pChunkID, "LIST") || drwav_fourcc_equal(pChunk
ID, "list")) {
        drwav_metadata_location listType = drwav_metadata_location_invalid;
        while (bytesRead < pChunkHeader->sizeInBytes) {
            drwav_uint8 subchunkId[4];
            drwav_uint8 subchunkSizeBuffer[4];
            drwav_uint64 subchunkDataSize;
            drwav_uint64 subchunkBytesRead = 0;
            drwav_uint64 bytesJustRead = drwav__metadata_parser_read(pParser, su
bchunkId, sizeof(subchunkId), &bytesRead);
            if (bytesJustRead != sizeof(subchunkId)) {
                break;
            }

            /*
            The first thing in a list chunk should be "adtl" or "INFO".

              - adtl means this list is a Associated Data List Chunk and will co
ntain labels, notes
                or labelled cue regions.
              - INFO means this list is an Info List Chunk containing info text
chunks such as IPRD
                which would specifies the album of this wav file.

            No data follows the adtl or INFO id so we just make note of what typ
e this list is and
            continue.
            */
            if (drwav_fourcc_equal(subchunkId, "adtl")) {
                listType = drwav_metadata_location_inside_adtl_list;
                continue;
            } else if (drwav_fourcc_equal(subchunkId, "INFO")) {
                listType = drwav_metadata_location_inside_info_list;
                continue;
            }

            bytesJustRead = drwav__metadata_parser_read(pParser, subchunkSizeBuf
fer, sizeof(subchunkSizeBuffer), &bytesRead);
            if (bytesJustRead != sizeof(subchunkSizeBuffer)) {
                break;
            }
            subchunkDataSize = drwav_bytes_to_u32(subchunkSizeBuffer);

            if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, drwav_met
adata_type_list_label, "labl") || drwav__chunk_matches(allowedMetadataTypes, sub
chunkId, drwav_metadata_type_list_note, "note")) {
                if (subchunkDataSize >= DRWAV_LIST_LABEL_OR_NOTE_BYTES) {
                    drwav_uint64 stringSizeWithNullTerm = subchunkDataSize - DRW
AV_LIST_LABEL_OR_NOTE_BYTES;
                    if (pParser->stage == drwav__metadata_parser_stage_count) {
                        pParser->metadataCount += 1;
                        drwav__metadata_request_extra_memory_for_stage_2(pParser
, (size_t)stringSizeWithNullTerm, 1);
                    } else {
                        subchunkBytesRead = drwav__read_list_label_or_note_to_me
tadata_obj(pParser, &pParser->pMetadata[pParser->metadataCursor], subchunkDataSi
ze, drwav_fourcc_equal(subchunkId, "labl") ? drwav_metadata_type_list_label : dr
wav_metadata_type_list_note);
                        if (subchunkBytesRead == subchunkDataSize) {
                            pParser->metadataCursor += 1;
                        } else {
                            /* Failed to parse. */
                        }
                    }
                } else {
                    /* Incorrectly formed chunk. */
                }
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_labelled_cue_region, "ltxt")) {
                if (subchunkDataSize >= DRWAV_LIST_LABELLED_TEXT_BYTES) {
                    drwav_uint64 stringSizeWithNullTerminator = subchunkDataSize
 - DRWAV_LIST_LABELLED_TEXT_BYTES;
                    if (pParser->stage == drwav__metadata_parser_stage_count) {
                        pParser->metadataCount += 1;
                        drwav__metadata_request_extra_memory_for_stage_2(pParser
, (size_t)stringSizeWithNullTerminator, 1);
                    } else {
                        subchunkBytesRead = drwav__read_list_labelled_cue_region
_to_metadata_obj(pParser, &pParser->pMetadata[pParser->metadataCursor], subchunk
DataSize);
                        if (subchunkBytesRead == subchunkDataSize) {
                            pParser->metadataCursor += 1;
                        } else {
                            /* Failed to parse. */
                        }
                    }
                } else {
                    /* Incorrectly formed chunk. */
                }
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_software, "ISFT")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_software);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_copyright, "ICOP")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_copyright);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_title, "INAM")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_title);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_artist, "IART")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_artist);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_comment, "ICMT")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_comment);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_date, "ICRD")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_date);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_genre, "IGNR")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_genre);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_album, "IPRD")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_album);
            } else if (drwav__chunk_matches(allowedMetadataTypes, subchunkId, dr
wav_metadata_type_list_info_tracknumber, "ITRK")) {
                subchunkBytesRead = drwav__metadata_process_info_text_chunk(pPar
ser, subchunkDataSize,  drwav_metadata_type_list_info_tracknumber);
            } else if ((allowedMetadataTypes & drwav_metadata_type_unknown) != 0
) {
                subchunkBytesRead = drwav__metadata_process_unknown_chunk(pParse
r, subchunkId, subchunkDataSize, listType);
            }

            bytesRead += subchunkBytesRead;
            DRWAV_ASSERT(subchunkBytesRead <= subchunkDataSize);

            if (subchunkBytesRead < subchunkDataSize) {
                drwav_uint64 bytesToSeek = subchunkDataSize - subchunkBytesRead;

                if (!pParser->onSeek(pParser->pReadSeekUserData, (int)bytesToSee
k, drwav_seek_origin_current)) {
                    break;
                }
                bytesRead += bytesToSeek;
            }

            if ((subchunkDataSize % 2) == 1) {
                if (!pParser->onSeek(pParser->pReadSeekUserData, 1, drwav_seek_o
rigin_current)) {
                    break;
                }
                bytesRead += 1;
            }
        }
    } else if ((allowedMetadataTypes & drwav_metadata_type_unknown) != 0) {
        bytesRead = drwav__metadata_process_unknown_chunk(pParser, pChunkID, pCh
unkHeader->sizeInBytes, drwav_metadata_location_top_level);
    }

    return bytesRead;
}


DRWAV_PRIVATE drwav_uint32 drwav_get_bytes_per_pcm_frame(drwav* pWav)
{
    drwav_uint32 bytesPerFrame;

    /*
    The bytes per frame is a bit ambiguous. It can be either be based on the bit
s per sample, or the block align. The way I'm doing it here
    is that if the bits per sample is a multiple of 8, use floor(bitsPerSample*c
hannels/8), otherwise fall back to the block align.
    */
    if ((pWav->bitsPerSample & 0x7) == 0) {
        /* Bits per sample is a multiple of 8. */
        bytesPerFrame = (pWav->bitsPerSample * pWav->fmt.channels) >> 3;
    } else {
        bytesPerFrame = pWav->fmt.blockAlign;
    }

    /* Validation for known formats. a-law and mu-law should be 1 byte per chann
el. If it's not, it's not decodable. */
    if (pWav->translatedFormatTag == DR_WAVE_FORMAT_ALAW || pWav->translatedForm
atTag == DR_WAVE_FORMAT_MULAW) {
        if (bytesPerFrame != pWav->fmt.channels) {
            return 0;   /* Invalid file. */
        }
    }

    return bytesPerFrame;
}

DRWAV_API drwav_uint16 drwav_fmt_get_format(const drwav_fmt* pFMT)
{
    if (pFMT == NULL) {
        return 0;
    }

    if (pFMT->formatTag != DR_WAVE_FORMAT_EXTENSIBLE) {
        return pFMT->formatTag;
    } else {
        return drwav_bytes_to_u16(pFMT->subFormat);    /* Only the first two byt
es are required. */
    }
}

DRWAV_PRIVATE drwav_bool32 drwav_preinit(drwav* pWav, drwav_read_proc onRead, dr
wav_seek_proc onSeek, void* pReadSeekUserData, const drwav_allocation_callbacks*
 pAllocationCallbacks)
{
    if (pWav == NULL || onRead == NULL || onSeek == NULL) {
        return DRWAV_FALSE;
    }

    DRWAV_ZERO_MEMORY(pWav, sizeof(*pWav));
    pWav->onRead    = onRead;
    pWav->onSeek    = onSeek;
    pWav->pUserData = pReadSeekUserData;
    pWav->allocationCallbacks = drwav_copy_allocation_callbacks_or_defaults(pAll
ocationCallbacks);

    if (pWav->allocationCallbacks.onFree == NULL || (pWav->allocationCallbacks.o
nMalloc == NULL && pWav->allocationCallbacks.onRealloc == NULL)) {
        return DRWAV_FALSE;    /* Invalid allocation callbacks. */
    }

    return DRWAV_TRUE;
}

DRWAV_PRIVATE drwav_bool32 drwav_init__internal(drwav* pWav, drwav_chunk_proc on
Chunk, void* pChunkUserData, drwav_uint32 flags)
{
    /* This function assumes drwav_preinit() has been called beforehand. */
    drwav_result result;
    drwav_uint64 cursor;    /* <-- Keeps track of the byte position so we can se
ek to specific locations. */
    drwav_bool32 sequential;
    drwav_uint8 riff[4];
    drwav_fmt fmt;
    unsigned short translatedFormatTag;
    drwav_uint64 dataChunkSize = 0;             /* <-- Important! Don't explicit
ly set this to 0 anywhere else. Calculation of the size of the data chunk is per
formed in different paths depending on the container. */
    drwav_uint64 sampleCountFromFactChunk = 0;  /* Same as dataChunkSize - make
sure this is the only place this is initialized to 0. */
    drwav_uint64 metadataStartPos;
    drwav__metadata_parser metadataParser;
    drwav_bool8 isProcessingMetadata = DRWAV_FALSE;
    drwav_bool8 foundChunk_fmt  = DRWAV_FALSE;
    drwav_bool8 foundChunk_data = DRWAV_FALSE;
    drwav_bool8 isAIFCFormType = DRWAV_FALSE;   /* Only used with AIFF. */
    drwav_uint64 aiffFrameCount = 0;

    cursor = 0;
    sequential = (flags & DRWAV_SEQUENTIAL) != 0;
    DRWAV_ZERO_OBJECT(&fmt);

    /* The first 4 bytes should be the RIFF identifier. */
    if (drwav__on_read(pWav->onRead, pWav->pUserData, riff, sizeof(riff), &curso
r) != sizeof(riff)) {
        return DRWAV_FALSE;
    }

    /*
    The first 4 bytes can be used to identify the container. For RIFF files it w
ill start with "RIFF" and for
    w64 it will start with "riff".
    */
    if (drwav_fourcc_equal(riff, "RIFF")) {
        pWav->container = drwav_container_riff;
    } else if (drwav_fourcc_equal(riff, "RIFX")) {
        pWav->container = drwav_container_rifx;
    } else if (drwav_fourcc_equal(riff, "riff")) {
        int i;
        drwav_uint8 riff2[12];

        pWav->container = drwav_container_w64;

        /* Check the rest of the GUID for validity. */
        if (drwav__on_read(pWav->onRead, pWav->pUserData, riff2, sizeof(riff2),
&cursor) != sizeof(riff2)) {
            return DRWAV_FALSE;
        }

        for (i = 0; i < 12; ++i) {
            if (riff2[i] != drwavGUID_W64_RIFF[i+4]) {
                return DRWAV_FALSE;
            }
        }
    } else if (drwav_fourcc_equal(riff, "RF64")) {
        pWav->container = drwav_container_rf64;
    } else if (drwav_fourcc_equal(riff, "FORM")) {
        pWav->container = drwav_container_aiff;
    } else {
        return DRWAV_FALSE;   /* Unknown or unsupported container. */
    }


    if (pWav->container == drwav_container_riff || pWav->container == drwav_cont
ainer_rifx || pWav->container == drwav_container_rf64) {
        drwav_uint8 chunkSizeBytes[4];
        drwav_uint8 wave[4];

        if (drwav__on_read(pWav->onRead, pWav->pUserData, chunkSizeBytes, sizeof
(chunkSizeBytes), &cursor) != sizeof(chunkSizeBytes)) {
            return DRWAV_FALSE;
        }

        if (pWav->container == drwav_container_riff || pWav->container == drwav_
container_rifx) {
            if (drwav_bytes_to_u32_ex(chunkSizeBytes, pWav->container) < 36) {
                /*
                I've had a report of a WAV file failing to load when the size of
 the WAVE chunk is not encoded
                and is instead just set to 0. I'm going to relax the validation
here to allow these files to
                load. Considering the chunk size isn't actually used this should
 be safe. With this change my
                test suite still passes.
                */
                /*return DRWAV_FALSE;*/    /* Chunk size should always be at lea
st 36 bytes. */
            }
        } else if (pWav->container == drwav_container_rf64) {
            if (drwav_bytes_to_u32_le(chunkSizeBytes) != 0xFFFFFFFF) {
                return DRWAV_FALSE;    /* Chunk size should always be set to -1/
0xFFFFFFFF for RF64. The actual size is retrieved later. */
            }
        } else {
            return DRWAV_FALSE; /* Should never hit this. */
        }

        if (drwav__on_read(pWav->onRead, pWav->pUserData, wave, sizeof(wave), &c
ursor) != sizeof(wave)) {
            return DRWAV_FALSE;
        }

        if (!drwav_fourcc_equal(wave, "WAVE")) {
            return DRWAV_FALSE;    /* Expecting "WAVE". */
        }
    } else if (pWav->container == drwav_container_w64) {
        drwav_uint8 chunkSizeBytes[8];
        drwav_uint8 wave[16];

        if (drwav__on_read(pWav->onRead, pWav->pUserData, chunkSizeBytes, sizeof
(chunkSizeBytes), &cursor) != sizeof(chunkSizeBytes)) {
            return DRWAV_FALSE;
        }

        if (drwav_bytes_to_u64(chunkSizeBytes) < 80) {
            return DRWAV_FALSE;
        }

        if (drwav__on_read(pWav->onRead, pWav->pUserData, wave, sizeof(wave), &c
ursor) != sizeof(wave)) {
            return DRWAV_FALSE;
        }

        if (!drwav_guid_equal(wave, drwavGUID_W64_WAVE)) {
            return DRWAV_FALSE;
        }
    } else if (pWav->container == drwav_container_aiff) {
        drwav_uint8 chunkSizeBytes[4];
        drwav_uint8 aiff[4];

        if (drwav__on_read(pWav->onRead, pWav->pUserData, chunkSizeBytes, sizeof
(chunkSizeBytes), &cursor) != sizeof(chunkSizeBytes)) {
            return DRWAV_FALSE;
        }

        if (drwav_bytes_to_u32_be(chunkSizeBytes) < 18) {
            return DRWAV_FALSE;
        }

        if (drwav__on_read(pWav->onRead, pWav->pUserData, aiff, sizeof(aiff), &c
ursor) != sizeof(aiff)) {
            return DRWAV_FALSE;
        }

        if (drwav_fourcc_equal(aiff, "AIFF")) {
            isAIFCFormType = DRWAV_FALSE;
        } else if (drwav_fourcc_equal(aiff, "AIFC")) {
            isAIFCFormType = DRWAV_TRUE;
        } else {
            return DRWAV_FALSE; /* Expecting "AIFF" or "AIFC". */
        }
    } else {
        return DRWAV_FALSE;
    }


    /* For RF64, the "ds64" chunk must come next, before the "fmt " chunk. */
    if (pWav->container == drwav_container_rf64) {
        drwav_uint8 sizeBytes[8];
        drwav_uint64 bytesRemainingInChunk;
        drwav_chunk_header header;
        result = drwav__read_chunk_header(pWav->onRead, pWav->pUserData, pWav->c
ontainer, &cursor, &header);
        if (result != DRWAV_SUCCESS) {
            return DRWAV_FALSE;
        }

        if (!drwav_fourcc_equal(header.id.fourcc, "ds64")) {
            return DRWAV_FALSE; /* Expecting "ds64". */
        }

        bytesRemainingInChunk = header.sizeInBytes + header.paddingSize;

        /* We don't care about the size of the RIFF chunk - skip it. */
        if (!drwav__seek_forward(pWav->onSeek, 8, pWav->pUserData)) {
            return DRWAV_FALSE;
        }
        bytesRemainingInChunk -= 8;
        cursor += 8;


        /* Next 8 bytes is the size of the "data" chunk. */
        if (drwav__on_read(pWav->onRead, pWav->pUserData, sizeBytes, sizeof(size
Bytes), &cursor) != sizeof(sizeBytes)) {
            return DRWAV_FALSE;
        }
        bytesRemainingInChunk -= 8;
        dataChunkSize = drwav_bytes_to_u64(sizeBytes);


        /* Next 8 bytes is the same count which we would usually derived from th
e FACT chunk if it was available. */
        if (drwav__on_read(pWav->onRead, pWav->pUserData, sizeBytes, sizeof(size
Bytes), &cursor) != sizeof(sizeBytes)) {
            return DRWAV_FALSE;
        }
        bytesRemainingInChunk -= 8;
        sampleCountFromFactChunk = drwav_bytes_to_u64(sizeBytes);


        /* Skip over everything else. */
        if (!drwav__seek_forward(pWav->onSeek, bytesRemainingInChunk, pWav->pUse
rData)) {
            return DRWAV_FALSE;
        }
        cursor += bytesRemainingInChunk;
    }


    metadataStartPos = cursor;

    /*
    Whether or not we are processing metadata controls how we load. We can load
more efficiently when
    metadata is not being processed, but we also cannot process metadata for Wav
e64 because I have not
    been able to test it. If someone is able to test this and provide a patch I'
m happy to enable it.

    Seqential mode cannot support metadata because it involves seeking backwards
.
    */
    isProcessingMetadata = !sequential && ((flags & DRWAV_WITH_METADATA) != 0);

    /* Don't allow processing of metadata with untested containers. */
    if (pWav->container != drwav_container_riff && pWav->container != drwav_cont
ainer_rf64) {
        isProcessingMetadata = DRWAV_FALSE;
    }

    DRWAV_ZERO_MEMORY(&metadataParser, sizeof(metadataParser));
    if (isProcessingMetadata) {
        metadataParser.onRead = pWav->onRead;
        metadataParser.onSeek = pWav->onSeek;
        metadataParser.pReadSeekUserData = pWav->pUserData;
        metadataParser.stage  = drwav__metadata_parser_stage_count;
    }


    /*
    From here on out, chunks might be in any order. In order to robustly handle
metadata we'll need
    to loop through every chunk and handle them as we find them. In sequential m
ode we need to get
    out of the loop as soon as we find the data chunk because we won't be able t
o seek back.
    */
    for (;;) {  /* For each chunk... */
        drwav_chunk_header header;
        drwav_uint64 chunkSize;

        result = drwav__read_chunk_header(pWav->onRead, pWav->pUserData, pWav->c
ontainer, &cursor, &header);
        if (result != DRWAV_SUCCESS) {
            break;
        }

        chunkSize = header.sizeInBytes;


        /*
        Always tell the caller about this chunk. We cannot do this in sequential
 mode because the
        callback is allowed to read from the file, in which case we'll need to r
ewind.
        */
        if (!sequential && onChunk != NULL) {
            drwav_uint64 callbackBytesRead = onChunk(pChunkUserData, pWav->onRea
d, pWav->onSeek, pWav->pUserData, &header, pWav->container, &fmt);

            /*
            dr_wav may need to read the contents of the chunk, so we now need to
 seek back to the position before
            we called the callback.
            */
            if (callbackBytesRead > 0) {
                if (drwav__seek_from_start(pWav->onSeek, cursor, pWav->pUserData
) == DRWAV_FALSE) {
                    return DRWAV_FALSE;
                }
            }
        }


        /* Explicitly handle known chunks first. */

        /* "fmt " */
        if (((pWav->container == drwav_container_riff || pWav->container == drwa
v_container_rifx || pWav->container == drwav_container_rf64) && drwav_fourcc_equ
al(header.id.fourcc, "fmt ")) ||
            ((pWav->container == drwav_container_w64) && drwav_guid_equal(header
.id.guid, drwavGUID_W64_FMT))) {
            drwav_uint8 fmtData[16];

            foundChunk_fmt = DRWAV_TRUE;

            if (pWav->onRead(pWav->pUserData, fmtData, sizeof(fmtData)) != sizeo
f(fmtData)) {
                return DRWAV_FALSE;
            }
            cursor += sizeof(fmtData);

            fmt.formatTag      = drwav_bytes_to_u16_ex(fmtData + 0,  pWav->conta
iner);
            fmt.channels       = drwav_bytes_to_u16_ex(fmtData + 2,  pWav->conta
iner);
            fmt.sampleRate     = drwav_bytes_to_u32_ex(fmtData + 4,  pWav->conta
iner);
            fmt.avgBytesPerSec = drwav_bytes_to_u32_ex(fmtData + 8,  pWav->conta
iner);
            fmt.blockAlign     = drwav_bytes_to_u16_ex(fmtData + 12, pWav->conta
iner);
            fmt.bitsPerSample  = drwav_bytes_to_u16_ex(fmtData + 14, pWav->conta
iner);

            fmt.extendedSize       = 0;
            fmt.validBitsPerSample = 0;
            fmt.channelMask        = 0;
            DRWAV_ZERO_MEMORY(fmt.subFormat, sizeof(fmt.subFormat));

            if (header.sizeInBytes > 16) {
                drwav_uint8 fmt_cbSize[2];
                int bytesReadSoFar = 0;

                if (pWav->onRead(pWav->pUserData, fmt_cbSize, sizeof(fmt_cbSize)
) != sizeof(fmt_cbSize)) {
                    return DRWAV_FALSE;    /* Expecting more data. */
                }
                cursor += sizeof(fmt_cbSize);

                bytesReadSoFar = 18;

                fmt.extendedSize = drwav_bytes_to_u16_ex(fmt_cbSize, pWav->conta
iner);
                if (fmt.extendedSize > 0) {
                    /* Simple validation. */
                    if (fmt.formatTag == DR_WAVE_FORMAT_EXTENSIBLE) {
                        if (fmt.extendedSize != 22) {
                            return DRWAV_FALSE;
                        }
                    }

                    if (fmt.formatTag == DR_WAVE_FORMAT_EXTENSIBLE) {
                        drwav_uint8 fmtext[22];

                        if (pWav->onRead(pWav->pUserData, fmtext, fmt.extendedSi
ze) != fmt.extendedSize) {
                            return DRWAV_FALSE;    /* Expecting more data. */
                        }

                        fmt.validBitsPerSample = drwav_bytes_to_u16_ex(fmtext +
0, pWav->container);
                        fmt.channelMask        = drwav_bytes_to_u32_ex(fmtext +
2, pWav->container);
                        drwav_bytes_to_guid(fmtext + 6, fmt.subFormat);
                    } else {
                        if (pWav->onSeek(pWav->pUserData, fmt.extendedSize, drwa
v_seek_origin_current) == DRWAV_FALSE) {
                            return DRWAV_FALSE;
                        }
                    }
                    cursor += fmt.extendedSize;

                    bytesReadSoFar += fmt.extendedSize;
                }

                /* Seek past any leftover bytes. For w64 the leftover will be de
fined based on the chunk size. */
                if (pWav->onSeek(pWav->pUserData, (int)(header.sizeInBytes - byt
esReadSoFar), drwav_seek_origin_current) == DRWAV_FALSE) {
                    return DRWAV_FALSE;
                }
                cursor += (header.sizeInBytes - bytesReadSoFar);
            }

            if (header.paddingSize > 0) {
                if (drwav__seek_forward(pWav->onSeek, header.paddingSize, pWav->
pUserData) == DRWAV_FALSE) {
                    break;
                }
                cursor += header.paddingSize;
            }

            /* Go to the next chunk. Don't include this chunk in metadata. */
            continue;
        }

        /* "data" */
        if (((pWav->container == drwav_container_riff || pWav->container == drwa
v_container_rifx || pWav->container == drwav_container_rf64) && drwav_fourcc_equ
al(header.id.fourcc, "data")) ||
            ((pWav->container == drwav_container_w64) && drwav_guid_equal(header
.id.guid, drwavGUID_W64_DATA))) {
            foundChunk_data = DRWAV_TRUE;

            pWav->dataChunkDataPos  = cursor;

            if (pWav->container != drwav_container_rf64) {  /* The data chunk si
ze for RF64 will always be set to 0xFFFFFFFF here. It was set to it's true value
 earlier. */
                dataChunkSize = chunkSize;
            }

            /* If we're running in sequential mode, or we're not reading metadat
a, we have enough now that we can get out of the loop. */
            if (sequential || !isProcessingMetadata) {
                break;      /* No need to keep reading beyond the data chunk. */
            } else {
                chunkSize += header.paddingSize;    /* <-- Make sure we seek pas
t the padding. */
                if (drwav__seek_forward(pWav->onSeek, chunkSize, pWav->pUserData
) == DRWAV_FALSE) {
                    break;
                }
                cursor += chunkSize;

                continue;   /* There may be some more metadata to read. */
            }
        }

        /* "fact". This is optional. Can use this to get the sample count which
is useful for compressed formats. For RF64 we retrieved the sample count from th
e ds64 chunk earlier. */
        if (((pWav->container == drwav_container_riff || pWav->container == drwa
v_container_rifx || pWav->container == drwav_container_rf64) && drwav_fourcc_equ
al(header.id.fourcc, "fact")) ||
            ((pWav->container == drwav_container_w64) && drwav_guid_equal(header
.id.guid, drwavGUID_W64_FACT))) {
            if (pWav->container == drwav_container_riff || pWav->container == dr
wav_container_rifx) {
                drwav_uint8 sampleCount[4];
                if (drwav__on_read(pWav->onRead, pWav->pUserData, &sampleCount,
4, &cursor) != 4) {
                    return DRWAV_FALSE;
                }

                chunkSize -= 4;

                /*
                The sample count in the "fact" chunk is either unreliable, or I'
m not understanding it properly. For now I am only enabling this
                for Microsoft ADPCM formats.
                */
                if (pWav->translatedFormatTag == DR_WAVE_FORMAT_ADPCM) {
                    sampleCountFromFactChunk = drwav_bytes_to_u32_ex(sampleCount
, pWav->container);
                } else {
                    sampleCountFromFactChunk = 0;
                }
            } else if (pWav->container == drwav_container_w64) {
                if (drwav__on_read(pWav->onRead, pWav->pUserData, &sampleCountFr
omFactChunk, 8, &cursor) != 8) {
                    return DRWAV_FALSE;
                }

                chunkSize -= 8;
            } else if (pWav->container == drwav_container_rf64) {
                /* We retrieved the sample count from the ds64 chunk earlier so
no need to do that here. */
            }

            /* Seek to the next chunk in preparation for the next iteration. */
            chunkSize += header.paddingSize;    /* <-- Make sure we seek past th
e padding. */
            if (drwav__seek_forward(pWav->onSeek, chunkSize, pWav->pUserData) ==
 DRWAV_FALSE) {
                break;
            }
            cursor += chunkSize;

            continue;
        }


        /* "COMM". AIFF/AIFC only. */
        if (pWav->container == drwav_container_aiff && drwav_fourcc_equal(header
.id.fourcc, "COMM")) {
            drwav_uint8 commData[24];
            drwav_uint32 commDataBytesToRead;
            drwav_uint16 channels;
            drwav_uint32 frameCount;
            drwav_uint16 sampleSizeInBits;
            drwav_int64  sampleRate;
            drwav_uint16 compressionFormat;

            foundChunk_fmt = DRWAV_TRUE;

            if (isAIFCFormType) {
                commDataBytesToRead = 24;
                if (header.sizeInBytes < commDataBytesToRead) {
                    return DRWAV_FALSE; /* Invalid COMM chunk. */
                }
            } else {
                commDataBytesToRead = 18;
                if (header.sizeInBytes != commDataBytesToRead) {
                    return DRWAV_FALSE; /* INVALID COMM chunk. */
                }
            }

            if (drwav__on_read(pWav->onRead, pWav->pUserData, commData, commData
BytesToRead, &cursor) != commDataBytesToRead) {
                return DRWAV_FALSE;
            }


            channels         = drwav_bytes_to_u16_ex     (commData + 0, pWav->co
ntainer);
            frameCount       = drwav_bytes_to_u32_ex     (commData + 2, pWav->co
ntainer);
            sampleSizeInBits = drwav_bytes_to_u16_ex     (commData + 6, pWav->co
ntainer);
            sampleRate       = drwav_aiff_extented_to_s64(commData + 8);

            if (sampleRate < 0 || sampleRate > 0xFFFFFFFF) {
                return DRWAV_FALSE; /* Invalid sample rate. */
            }

            if (isAIFCFormType) {
                const drwav_uint8* type = commData + 18;

                if (drwav_fourcc_equal(type, "NONE")) {
                    compressionFormat = DR_WAVE_FORMAT_PCM; /* PCM, big-endian.
*/
                } else if (drwav_fourcc_equal(type, "raw ")) {
                    compressionFormat = DR_WAVE_FORMAT_PCM;

                    /* In my testing, it looks like when the "raw " compression
type is used, 8-bit samples should be considered unsigned. */
                    if (sampleSizeInBits == 8) {
                        pWav->aiff.isUnsigned = DRWAV_TRUE;
                    }
                } else if (drwav_fourcc_equal(type, "sowt")) {
                    compressionFormat = DR_WAVE_FORMAT_PCM; /* PCM, little-endia
n. */
                    pWav->aiff.isLE = DRWAV_TRUE;
                } else if (drwav_fourcc_equal(type, "fl32") || drwav_fourcc_equa
l(type, "fl64") || drwav_fourcc_equal(type, "FL32") || drwav_fourcc_equal(type,
"FL64")) {
                    compressionFormat = DR_WAVE_FORMAT_IEEE_FLOAT;
                } else if (drwav_fourcc_equal(type, "alaw") || drwav_fourcc_equa
l(type, "ALAW")) {
                    compressionFormat = DR_WAVE_FORMAT_ALAW;
                } else if (drwav_fourcc_equal(type, "ulaw") || drwav_fourcc_equa
l(type, "ULAW")) {
                    compressionFormat = DR_WAVE_FORMAT_MULAW;
                } else if (drwav_fourcc_equal(type, "ima4")) {
                    compressionFormat = DR_WAVE_FORMAT_DVI_ADPCM;
                    sampleSizeInBits  = 4;

                    /*
                    I haven't been able to figure out how to get correct decodin
g for IMA ADPCM. Until this is figured out
                    we'll need to abort when we encounter such an encoding. Advi
ce welcome!
                    */
                    (void)compressionFormat;
                    (void)sampleSizeInBits;

                    return DRWAV_FALSE;
                } else {
                    return DRWAV_FALSE; /* Unknown or unsupported compression fo
rmat. Need to abort. */
                }
            } else {
                compressionFormat = DR_WAVE_FORMAT_PCM; /* It's a standard AIFF
form which is always compressed. */
            }

            /* With AIFF we want to use the explicitly defined frame count rathe
r than deriving it from the size of the chunk. */
            aiffFrameCount = frameCount;

            /* We should now have enough information to fill out our fmt structu
re. */
            fmt.formatTag      = compressionFormat;
            fmt.channels       = channels;
            fmt.sampleRate     = (drwav_uint32)sampleRate;
            fmt.bitsPerSample  = sampleSizeInBits;
            fmt.blockAlign     = (drwav_uint16)(fmt.channels * fmt.bitsPerSample
 / 8);
            fmt.avgBytesPerSec = fmt.blockAlign * fmt.sampleRate;

            if (fmt.blockAlign == 0 && compressionFormat == DR_WAVE_FORMAT_DVI_A
DPCM) {
                fmt.blockAlign = 34 * fmt.channels;
            }

            /*
            Weird one. I've seen some alaw and ulaw encoded files that for some
reason set the bits per sample to 16 when
            it should be 8. To get this working I need to explicitly check for t
his and change it.
            */
            if (compressionFormat == DR_WAVE_FORMAT_ALAW || compressionFormat ==
 DR_WAVE_FORMAT_MULAW) {
                if (fmt.bitsPerSample > 8) {
                    fmt.bitsPerSample = 8;
                    fmt.blockAlign = fmt.channels;
                }
            }

            /* In AIFF, samples are padded to 8 byte boundaries. We need to roun
d up our bits per sample here. */
            fmt.bitsPerSample += (fmt.bitsPerSample & 7);


            /* If the form type is AIFC there will be some additional data in th
e chunk. We need to seek past it. */
            if (isAIFCFormType) {
                if (drwav__seek_forward(pWav->onSeek, (chunkSize - commDataBytes
ToRead), pWav->pUserData) == DRWAV_FALSE) {
                    return DRWAV_FALSE;
                }
                cursor += (chunkSize - commDataBytesToRead);
            }

            /* Don't fall through or else we'll end up treating this chunk as me
tadata which is incorrect. */
            continue;
        }


        /* "SSND". AIFF/AIFC only. This is the AIFF equivalent of the "data" chu
nk. */
        if (pWav->container == drwav_container_aiff && drwav_fourcc_equal(header
.id.fourcc, "SSND")) {
            drwav_uint8 offsetAndBlockSizeData[8];
            drwav_uint32 offset;

            foundChunk_data = DRWAV_TRUE;

            if (drwav__on_read(pWav->onRead, pWav->pUserData, offsetAndBlockSize
Data, sizeof(offsetAndBlockSizeData), &cursor) != sizeof(offsetAndBlockSizeData)
) {
                return DRWAV_FALSE;
            }

            /* The position of the audio data starts at an offset. */
            offset = drwav_bytes_to_u32_ex(offsetAndBlockSizeData + 0, pWav->con
tainer);
            pWav->dataChunkDataPos = cursor + offset;

            /* The data chunk size needs to be reduced by the offset or else see
king will break. */
            dataChunkSize = chunkSize;
            if (dataChunkSize  > offset) {
                dataChunkSize -= offset;
            } else {
                dataChunkSize = 0;
            }

            if (sequential) {
                if (foundChunk_fmt) {   /* <-- Name is misleading, but will be s
et to true if the COMM chunk has been parsed. */
                    /*
                    Getting here means we're opening in sequential mode and we'v
e found the SSND (data) and COMM (fmt) chunks. We need
                    to get out of the loop here or else we'll end up going past
the data chunk and will have no way of getting back to
                    it since we're not allowed to seek backwards.

                    One subtle detail here is that there is an offset with the S
SND chunk. We need to make sure we seek past this offset
                    so we're left sitting on the first byte of actual audio data
.
                    */
                    if (drwav__seek_forward(pWav->onSeek, offset, pWav->pUserDat
a) == DRWAV_FALSE) {
                        return DRWAV_FALSE;
                    }
                    cursor += offset;

                    break;
                } else {
                    /*
                    Getting here means the COMM chunk was not found. In sequenti
al mode, if we haven't yet found the COMM chunk
                    we'll need to abort because we can't be doing a backwards se
ek back to the SSND chunk in order to read the
                    data. For this reason, this configuration of AIFF files are
not supported with sequential mode.
                    */
                    return DRWAV_FALSE;
                }
            } else {
                chunkSize += header.paddingSize;                /* <-- Make sure
 we seek past the padding. */
                chunkSize -= sizeof(offsetAndBlockSizeData);    /* <-- This was
read earlier. */

                if (drwav__seek_forward(pWav->onSeek, chunkSize, pWav->pUserData
) == DRWAV_FALSE) {
                    break;
                }
                cursor += chunkSize;

                continue;   /* There may be some more metadata to read. */
            }
        }


        /* Getting here means it's not a chunk that we care about internally, bu
t might need to be handled as metadata by the caller. */
        if (isProcessingMetadata) {
            drwav__metadata_process_chunk(&metadataParser, &header, drwav_metada
ta_type_all_including_unknown);

            /* Go back to the start of the chunk so we can normalize the positio
n of the cursor. */
            if (drwav__seek_from_start(pWav->onSeek, cursor, pWav->pUserData) ==
 DRWAV_FALSE) {
                break;  /* Failed to seek. Can't reliable read the remaining chu
nks. Get out. */
            }
        }


        /* Make sure we skip past the content of this chunk before we go to the
next one. */
        chunkSize += header.paddingSize;    /* <-- Make sure we seek past the pa
dding. */
        if (drwav__seek_forward(pWav->onSeek, chunkSize, pWav->pUserData) == DRW
AV_FALSE) {
            break;
        }
        cursor += chunkSize;
    }

    /* There's some mandatory chunks that must exist. If they were not found in
the iteration above we must abort. */
    if (!foundChunk_fmt || !foundChunk_data) {
        return DRWAV_FALSE;
    }

    /* Basic validation. */
    if ((fmt.sampleRate    == 0 || fmt.sampleRate    > DRWAV_MAX_SAMPLE_RATE
) ||
        (fmt.channels      == 0 || fmt.channels      > DRWAV_MAX_CHANNELS
) ||
        (fmt.bitsPerSample == 0 || fmt.bitsPerSample > DRWAV_MAX_BITS_PER_SAMPLE
) ||
        fmt.blockAlign == 0) {
        return DRWAV_FALSE; /* Probably an invalid WAV file. */
    }

    /* Translate the internal format. */
    translatedFormatTag = fmt.formatTag;
    if (translatedFormatTag == DR_WAVE_FORMAT_EXTENSIBLE) {
        translatedFormatTag = drwav_bytes_to_u16_ex(fmt.subFormat + 0, pWav->con
tainer);
    }

    /* We may have moved passed the data chunk. If so we need to move back. If r
unning in sequential mode we can assume we are already sitting on the data chunk
. */
    if (!sequential) {
        if (!drwav__seek_from_start(pWav->onSeek, pWav->dataChunkDataPos, pWav->
pUserData)) {
            return DRWAV_FALSE;
        }
        cursor = pWav->dataChunkDataPos;
    }


    /*
    At this point we should have done the initial parsing of each of our chunks,
 but we now need to
    do a second pass to extract the actual contents of the metadata (the first p
ass just calculated
    the length of the memory allocation).

    We only do this if we've actually got metadata to parse.
    */
    if (isProcessingMetadata && metadataParser.metadataCount > 0) {
        if (drwav__seek_from_start(pWav->onSeek, metadataStartPos, pWav->pUserDa
ta) == DRWAV_FALSE) {
            return DRWAV_FALSE;
        }

        result = drwav__metadata_alloc(&metadataParser, &pWav->allocationCallbac
ks);
        if (result != DRWAV_SUCCESS) {
            return DRWAV_FALSE;
        }

        metadataParser.stage = drwav__metadata_parser_stage_read;

        for (;;) {
            drwav_chunk_header header;
            drwav_uint64 metadataBytesRead;

            result = drwav__read_chunk_header(pWav->onRead, pWav->pUserData, pWa
v->container, &cursor, &header);
            if (result != DRWAV_SUCCESS) {
                break;
            }

            metadataBytesRead = drwav__metadata_process_chunk(&metadataParser, &
header, drwav_metadata_type_all_including_unknown);

            /* Move to the end of the chunk so we can keep iterating. */
            if (drwav__seek_forward(pWav->onSeek, (header.sizeInBytes + header.p
addingSize) - metadataBytesRead, pWav->pUserData) == DRWAV_FALSE) {
                drwav_free(metadataParser.pMetadata, &pWav->allocationCallbacks)
;
                return DRWAV_FALSE;
            }
        }

        /* Getting here means we're finished parsing the metadata. */
        pWav->pMetadata     = metadataParser.pMetadata;
        pWav->metadataCount = metadataParser.metadataCount;
    }


    /* At this point we should be sitting on the first byte of the raw audio dat
a. */
    if (drwav__seek_from_start(pWav->onSeek, pWav->dataChunkDataPos, pWav->pUser
Data) == DRWAV_FALSE) {
        drwav_free(pWav->pMetadata, &pWav->allocationCallbacks);
        return DRWAV_FALSE;
    }

    /*
    I've seen a WAV file in the wild where a RIFF-ecapsulated file has the size
of it's "RIFF" and
    "data" chunks set to 0xFFFFFFFF when the file is definitely not that big. In
 this case we're
    going to have to calculate the size by reading and discarding bytes, and the
n seeking back. We
    cannot do this in sequential mode. We just assume that the rest of the file
is audio data.
    */
    if (dataChunkSize == 0xFFFFFFFF && (pWav->container == drwav_container_riff
|| pWav->container == drwav_container_rifx) && pWav->isSequentialWrite == DRWAV_
FALSE) {
        dataChunkSize = 0;

        for (;;) {
            drwav_uint8 temp[4096];
            size_t bytesRead = pWav->onRead(pWav->pUserData, temp, sizeof(temp))
;
            dataChunkSize += bytesRead;

            if (bytesRead < sizeof(temp)) {
                break;
            }
        }
    }

    pWav->fmt                 = fmt;
    pWav->sampleRate          = fmt.sampleRate;
    pWav->channels            = fmt.channels;
    pWav->bitsPerSample       = fmt.bitsPerSample;
    pWav->translatedFormatTag = translatedFormatTag;

    /*
    I've had a report where files would start glitching after seeking. The reaso
n for this is the data
    chunk is not a clean multiple of the PCM frame size in bytes. Where this bec
omes a problem is when
    seeking, because the number of bytes remaining in the data chunk is used to
calculate the current
    byte position. If this byte position is not aligned to the number of bytes i
n a PCM frame, it will
    result in the seek not being cleanly positioned at the start of the PCM fram
e thereby resulting in
    all decoded frames after that being corrupted.

    To address this, we need to round the data chunk size down to the nearest mu
ltiple of the frame size.
    */
    if (!drwav__is_compressed_format_tag(translatedFormatTag)) {
        drwav_uint32 bytesPerFrame = drwav_get_bytes_per_pcm_frame(pWav);
        if (bytesPerFrame > 0) {
            dataChunkSize -= (dataChunkSize % bytesPerFrame);
        }
    }

    pWav->bytesRemaining      = dataChunkSize;
    pWav->dataChunkDataSize   = dataChunkSize;

    if (sampleCountFromFactChunk != 0) {
        pWav->totalPCMFrameCount = sampleCountFromFactChunk;
    } else if (aiffFrameCount != 0) {
        pWav->totalPCMFrameCount = aiffFrameCount;
    } else {
        drwav_uint32 bytesPerFrame = drwav_get_bytes_per_pcm_frame(pWav);
        if (bytesPerFrame == 0) {
            drwav_free(pWav->pMetadata, &pWav->allocationCallbacks);
            return DRWAV_FALSE; /* Invalid file. */
        }

        pWav->totalPCMFrameCount = dataChunkSize / bytesPerFrame;

        if (pWav->translatedFormatTag == DR_WAVE_FORMAT_ADPCM) {
            drwav_uint64 totalBlockHeaderSizeInBytes;
            drwav_uint64 blockCount = dataChunkSize / fmt.blockAlign;

            /* Make sure any trailing partial block is accounted for. */
            if ((blockCount * fmt.blockAlign) < dataChunkSize) {
                blockCount += 1;
            }

            /* We decode two samples per byte. There will be blockCount headers
in the data chunk. This is enough to know how to calculate the total PCM frame c
ount. */
            totalBlockHeaderSizeInBytes = blockCount * (6*fmt.channels);
            pWav->totalPCMFrameCount = ((dataChunkSize - totalBlockHeaderSizeInB
ytes) * 2) / fmt.channels;
        }
        if (pWav->translatedFormatTag == DR_WAVE_FORMAT_DVI_ADPCM) {
            drwav_uint64 totalBlockHeaderSizeInBytes;
            drwav_uint64 blockCount = dataChunkSize / fmt.blockAlign;

            /* Make sure any trailing partial block is accounted for. */
            if ((blockCount * fmt.blockAlign) < dataChunkSize) {
                blockCount += 1;
            }

            /* We decode two samples per byte. There will be blockCount headers
in the data chunk. This is enough to know how to calculate the total PCM frame c
ount. */
            totalBlockHeaderSizeInBytes = blockCount * (4*fmt.channels);
            pWav->totalPCMFrameCount = ((dataChunkSize - totalBlockHeaderSizeInB
ytes) * 2) / fmt.channels;

            /* The header includes a decoded sample for each channel which acts
as the initial predictor sample. */
            pWav->totalPCMFrameCount += blockCount;
        }
    }

    /* Some formats only support a certain number of channels. */
    if (pWav->translatedFormatTag == DR_WAVE_FORMAT_ADPCM || pWav->translatedFor
matTag == DR_WAVE_FORMAT_DVI_ADPCM) {
        if (pWav->channels > 2) {
            drwav_free(pWav->pMetadata, &pWav->allocationCallbacks);
            return DRWAV_FALSE;
        }
    }

    /* The number of bytes per frame must be known. If not, it's an invalid file
 and not decodable. */
    if (drwav_get_bytes_per_pcm_frame(pWav) == 0) {
        drwav_free(pWav->pMetadata, &pWav->allocationCallbacks);
        return DRWAV_FALSE;
    }

#ifdef DR_WAV_LIBSNDFILE_COMPAT
    /*
    I use libsndfile as a benchmark for testing, however in the version I'm usin
g (from the Windows installer on the libsndfile website),
    it appears the total sample count libsndfile uses for MS-ADPCM is incorrect.
 It would seem they are computing the total sample count
    from the number of blocks, however this results in the inclusion of extra si
lent samples at the end of the last block. The correct
    way to know the total sample count is to inspect the "fact" chunk, which sho
uld always be present for compressed formats, and should
    always include the sample count. This little block of code below is only use
d to emulate the libsndfile logic so I can properly run my
    correctness tests against libsndfile, and is disabled by default.
    */
    if (pWav->translatedFormatTag == DR_WAVE_FORMAT_ADPCM) {
        drwav_uint64 blockCount = dataChunkSize / fmt.blockAlign;
        pWav->totalPCMFrameCount = (((blockCount * (fmt.blockAlign - (6*pWav->ch
annels))) * 2)) / fmt.channels;  /* x2 because two samples per byte. */
    }
    if (pWav->translatedFormatTag == DR_WAVE_FORMAT_DVI_ADPCM) {
        drwav_uint64 blockCount = dataChunkSize / fmt.blockAlign;
        pWav->totalPCMFrameCount = (((blockCount * (fmt.blockAlign - (4*pWav->ch
annels))) * 2) + (blockCount * pWav->channels)) / fmt.channels;
    }
#endif

    return DRWAV_TRUE;
}

DRWAV_API drwav_bool32 drwav_init(drwav* pWav, drwav_read_proc onRead, drwav_see
k_proc onSeek, void* pUserData, const drwav_allocation_callbacks* pAllocationCal
lbacks)
{
    return drwav_init_ex(pWav, onRead, onSeek, NULL, pUserData, NULL, 0, pAlloca
tionCallbacks);
}

DRWAV_API drwav_bool32 drwav_init_ex(drwav* pWav, drwav_read_proc onRead, drwav_
seek_proc onSeek, drwav_chunk_proc onChunk, void* pReadSeekUserData, void* pChun
kUserData, drwav_uint32 flags, const drwav_allocation_callbacks* pAllocationCall
backs)
{
    if (!drwav_preinit(pWav, onRead, onSeek, pReadSeekUserData, pAllocationCallb
acks)) {
        return DRWAV_FALSE;
    }

    return drwav_init__internal(pWav, onChunk, pChunkUserData, flags);
}

DRWAV_API drwav_bool32 drwav_init_with_metadata(drwav* pWav, drwav_read_proc onR
ead, drwav_seek_proc onSeek, void* pUserData, drwav_uint32 flags, const drwav_al
location_callbacks* pAllocationCallbacks)
{
    if (!drwav_preinit(pWav, onRead, onSeek, pUserData, pAllocationCallbacks)) {
        return DRWAV_FALSE;
    }

    return drwav_init__internal(pWav, NULL, NULL, flags | DRWAV_WITH_METADATA);
}

DRWAV_API drwav_metadata* drwav_take_ownership_of_metadata(drwav* pWav)
{
    drwav_metadata *result = pWav->pMetadata;

    pWav->pMetadata     = NULL;
    pWav->metadataCount = 0;

    return result;
}


DRWAV_PRIVATE size_t drwav__write(drwav* pWav, const void* pData, size_t dataSiz
e)
{
    DRWAV_ASSERT(pWav          != NULL);
    DRWAV_ASSERT(pWav->onWrite != NULL);

    /* Generic write. Assumes no byte reordering required. */
    return pWav->onWrite(pWav->pUserData, pData, dataSize);
}

DRWAV_PRIVATE size_t drwav__write_byte(drwav* pWav, drwav_uint8 byte)
{
    DRWAV_ASSERT(pWav          != NULL);
    DRWAV_ASSERT(pWav->onWrite != NULL);

    return pWav->onWrite(pWav->pUserData, &byte, 1);
}

DRWAV_PRIVATE size_t drwav__write_u16ne_to_le(drwav* pWav, drwav_uint16 value)
{
    DRWAV_ASSERT(pWav          != NULL);
    DRWAV_ASSERT(pWav->onWrite != NULL);

    if (!drwav__is_little_endian()) {
        value = drwav__bswap16(value);
    }

    return drwav__write(pWav, &value, 2);
}

DRWAV_PRIVATE size_t drwav__write_u32ne_to_le(drwav* pWav, drwav_uint32 value)
{
    DRWAV_ASSERT(pWav          != NULL);
    DRWAV_ASSERT(pWav->onWrite != NULL);

    if (!drwav__is_little_endian()) {
        value = drwav__bswap32(value);
    }

    return drwav__write(pWav, &value, 4);
}

DRWAV_PRIVATE size_t drwav__write_u64ne_to_le(drwav* pWav, drwav_uint64 value)
{
    DRWAV_ASSERT(pWav          != NULL);
    DRWAV_ASSERT(pWav->onWrite != NULL);

    if (!drwav__is_little_endian()) {
        value = drwav__bswap64(value);
    }

    return drwav__write(pWav, &value, 8);
}

DRWAV_PRIVATE size_t drwav__write_f32ne_to_le(drwav* pWav, float value)
{
    union {
       drwav_uint32 u32;
       float f32;
    } u;

    DRWAV_ASSERT(pWav          != NULL);
    DRWAV_ASSERT(pWav->onWrite != NULL);

    u.f32 = value;

    if (!drwav__is_little_endian()) {
        u.u32 = drwav__bswap32(u.u32);
    }

    return drwav__write(pWav, &u.u32, 4);
}

DRWAV_PRIVATE size_t drwav__write_or_count(drwav* pWav, const void* pData, size_
t dataSize)
{
    if (pWav == NULL) {
        return dataSize;
    }

    return drwav__write(pWav, pData, dataSize);
}

DRWAV_PRIVATE size_t drwav__write_or_count_byte(drwav* pWav, drwav_uint8 byte)
{
    if (pWav == NULL) {
        return 1;
    }

    return drwav__write_byte(pWav, byte);
}

DRWAV_PRIVATE size_t drwav__write_or_count_u16ne_to_le(drwav* pWav, drwav_uint16
 value)
{
    if (pWav == NULL) {
        return 2;
    }

    return drwav__write_u16ne_to_le(pWav, value);
}

DRWAV_PRIVATE size_t drwav__write_or_count_u32ne_to_le(drwav* pWav, drwav_uint32
 value)
{
    if (pWav == NULL) {
        return 4;
    }

    return drwav__write_u32ne_to_le(pWav, value);
}

#if 0   /* Unused for now. */
DRWAV_PRIVATE size_t drwav__write_or_count_u64ne_to_le(drwav* pWav, drwav_uint64
 value)
{
    if (pWav == NULL) {
        return 8;
    }

    return drwav__write_u64ne_to_le(pWav, value);
}
#endif

DRWAV_PRIVATE size_t drwav__write_or_count_f32ne_to_le(drwav* pWav, float value)
{
    if (pWav == NULL) {
        return 4;
    }

    return drwav__write_f32ne_to_le(pWav, value);
}

DRWAV_PRIVATE size_t drwav__write_or_count_string_to_fixed_size_buf(drwav* pWav,
 char* str, size_t bufFixedSize)
{
    size_t len;

    if (pWav == NULL) {
        return bufFixedSize;
    }

    len = drwav__strlen_clamped(str, bufFixedSize);
    drwav__write_or_count(pWav, str, len);

    if (len < bufFixedSize) {
        size_t i;
        for (i = 0; i < bufFixedSize - len; ++i) {
            drwav__write_byte(pWav, 0);
        }
    }

    return bufFixedSize;
}


/* pWav can be NULL meaning just count the bytes that would be written. */
DRWAV_PRIVATE size_t drwav__write_or_count_metadata(drwav* pWav, drwav_metadata*
 pMetadatas, drwav_uint32 metadataCount)
{
    size_t bytesWritten = 0;
    drwav_bool32 hasListAdtl = DRWAV_FALSE;
    drwav_bool32 hasListInfo = DRWAV_FALSE;
    drwav_uint32 iMetadata;

    if (pMetadatas == NULL || metadataCount == 0) {
        return 0;
    }

    for (iMetadata = 0; iMetadata < metadataCount; ++iMetadata) {
        drwav_metadata* pMetadata = &pMetadatas[iMetadata];
        drwav_uint32 chunkSize = 0;

        if ((pMetadata->type & drwav_metadata_type_list_all_info_strings) || (pM
etadata->type == drwav_metadata_type_unknown && pMetadata->data.unknown.chunkLoc
ation == drwav_metadata_location_inside_info_list)) {
            hasListInfo = DRWAV_TRUE;
        }

        if ((pMetadata->type & drwav_metadata_type_list_all_adtl) || (pMetadata-
>type == drwav_metadata_type_unknown && pMetadata->data.unknown.chunkLocation ==
 drwav_metadata_location_inside_adtl_list)) {
            hasListAdtl = DRWAV_TRUE;
        }

        switch (pMetadata->type) {
            case drwav_metadata_type_smpl:
            {
                drwav_uint32 iLoop;

                chunkSize = DRWAV_SMPL_BYTES + DRWAV_SMPL_LOOP_BYTES * pMetadata
->data.smpl.sampleLoopCount + pMetadata->data.smpl.samplerSpecificDataSizeInByte
s;

                bytesWritten += drwav__write_or_count(pWav, "smpl", 4);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSiz
e);

                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.manufacturerId);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.productId);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.samplePeriodNanoseconds);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.midiUnityNote);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.midiPitchFraction);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.smpteFormat);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.smpteOffset);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.sampleLoopCount);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.smpl.samplerSpecificDataSizeInBytes);

                for (iLoop = 0; iLoop < pMetadata->data.smpl.sampleLoopCount; ++
iLoop) {
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.smpl.pLoops[iLoop].cuePointId);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.smpl.pLoops[iLoop].type);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.smpl.pLoops[iLoop].firstSampleOffset);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.smpl.pLoops[iLoop].lastSampleOffset);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.smpl.pLoops[iLoop].sampleFraction);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.smpl.pLoops[iLoop].playCount);
                }

                if (pMetadata->data.smpl.samplerSpecificDataSizeInBytes > 0) {
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
smpl.pSamplerSpecificData, pMetadata->data.smpl.samplerSpecificDataSizeInBytes);
                }
            } break;

            case drwav_metadata_type_inst:
            {
                chunkSize = DRWAV_INST_BYTES;

                bytesWritten += drwav__write_or_count(pWav, "inst", 4);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSiz
e);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.midiUnityNote, 1);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.fineTuneCents, 1);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.gainDecibels, 1);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.lowNote, 1);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.highNote, 1);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.lowVelocity, 1);
                bytesWritten += drwav__write_or_count(pWav, &pMetadata->data.ins
t.highVelocity, 1);
            } break;

            case drwav_metadata_type_cue:
            {
                drwav_uint32 iCuePoint;

                chunkSize = DRWAV_CUE_BYTES + DRWAV_CUE_POINT_BYTES * pMetadata-
>data.cue.cuePointCount;

                bytesWritten += drwav__write_or_count(pWav, "cue ", 4);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSiz
e);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.cue.cuePointCount);
                for (iCuePoint = 0; iCuePoint < pMetadata->data.cue.cuePointCoun
t; ++iCuePoint) {
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.cue.pCuePoints[iCuePoint].id);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.cue.pCuePoints[iCuePoint].playOrderPosition);
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
cue.pCuePoints[iCuePoint].dataChunkId, 4);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.cue.pCuePoints[iCuePoint].chunkStart);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.cue.pCuePoints[iCuePoint].blockStart);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.cue.pCuePoints[iCuePoint].sampleOffset);
                }
            } break;

            case drwav_metadata_type_acid:
            {
                chunkSize = DRWAV_ACID_BYTES;

                bytesWritten += drwav__write_or_count(pWav, "acid", 4);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSiz
e);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.acid.flags);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.acid.midiUnityNote);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.acid.reserved1);
                bytesWritten += drwav__write_or_count_f32ne_to_le(pWav, pMetadat
a->data.acid.reserved2);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMetadat
a->data.acid.numBeats);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.acid.meterDenominator);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.acid.meterNumerator);
                bytesWritten += drwav__write_or_count_f32ne_to_le(pWav, pMetadat
a->data.acid.tempo);
            } break;

            case drwav_metadata_type_bext:
            {
                char reservedBuf[DRWAV_BEXT_RESERVED_BYTES];
                drwav_uint32 timeReferenceLow;
                drwav_uint32 timeReferenceHigh;

                chunkSize = DRWAV_BEXT_BYTES + pMetadata->data.bext.codingHistor
ySize;

                bytesWritten += drwav__write_or_count(pWav, "bext", 4);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSiz
e);

                bytesWritten += drwav__write_or_count_string_to_fixed_size_buf(p
Wav, pMetadata->data.bext.pDescription, DRWAV_BEXT_DESCRIPTION_BYTES);
                bytesWritten += drwav__write_or_count_string_to_fixed_size_buf(p
Wav, pMetadata->data.bext.pOriginatorName, DRWAV_BEXT_ORIGINATOR_NAME_BYTES);
                bytesWritten += drwav__write_or_count_string_to_fixed_size_buf(p
Wav, pMetadata->data.bext.pOriginatorReference, DRWAV_BEXT_ORIGINATOR_REF_BYTES)
;
                bytesWritten += drwav__write_or_count(pWav, pMetadata->data.bext
.pOriginationDate, sizeof(pMetadata->data.bext.pOriginationDate));
                bytesWritten += drwav__write_or_count(pWav, pMetadata->data.bext
.pOriginationTime, sizeof(pMetadata->data.bext.pOriginationTime));

                timeReferenceLow  = (drwav_uint32)(pMetadata->data.bext.timeRefe
rence & 0xFFFFFFFF);
                timeReferenceHigh = (drwav_uint32)(pMetadata->data.bext.timeRefe
rence >> 32);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, timeRefe
renceLow);
                bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, timeRefe
renceHigh);

                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.bext.version);
                bytesWritten += drwav__write_or_count(pWav, pMetadata->data.bext
.pUMID, DRWAV_BEXT_UMID_BYTES);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.bext.loudnessValue);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.bext.loudnessRange);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.bext.maxTruePeakLevel);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.bext.maxMomentaryLoudness);
                bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMetadat
a->data.bext.maxShortTermLoudness);

                DRWAV_ZERO_MEMORY(reservedBuf, sizeof(reservedBuf));
                bytesWritten += drwav__write_or_count(pWav, reservedBuf, sizeof(
reservedBuf));

                if (pMetadata->data.bext.codingHistorySize > 0) {
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
bext.pCodingHistory, pMetadata->data.bext.codingHistorySize);
                }
            } break;

            case drwav_metadata_type_unknown:
            {
                if (pMetadata->data.unknown.chunkLocation == drwav_metadata_loca
tion_top_level) {
                    chunkSize = pMetadata->data.unknown.dataSizeInBytes;

                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
unknown.id, 4);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chun
kSize);
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
unknown.pData, pMetadata->data.unknown.dataSizeInBytes);
                }
            } break;

            default: break;
        }
        if ((chunkSize % 2) != 0) {
            bytesWritten += drwav__write_or_count_byte(pWav, 0);
        }
    }

    if (hasListInfo) {
        drwav_uint32 chunkSize = 4; /* Start with 4 bytes for "INFO". */
        for (iMetadata = 0; iMetadata < metadataCount; ++iMetadata) {
            drwav_metadata* pMetadata = &pMetadatas[iMetadata];

            if ((pMetadata->type & drwav_metadata_type_list_all_info_strings)) {
                chunkSize += 8; /* For id and string size. */
                chunkSize += pMetadata->data.infoText.stringLength + 1; /* Inclu
de null terminator. */
            } else if (pMetadata->type == drwav_metadata_type_unknown && pMetada
ta->data.unknown.chunkLocation == drwav_metadata_location_inside_info_list) {
                chunkSize += 8; /* For id string size. */
                chunkSize += pMetadata->data.unknown.dataSizeInBytes;
            }

            if ((chunkSize % 2) != 0) {
                chunkSize += 1;
            }
        }

        bytesWritten += drwav__write_or_count(pWav, "LIST", 4);
        bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSize);
        bytesWritten += drwav__write_or_count(pWav, "INFO", 4);

        for (iMetadata = 0; iMetadata < metadataCount; ++iMetadata) {
            drwav_metadata* pMetadata = &pMetadatas[iMetadata];
            drwav_uint32 subchunkSize = 0;

            if (pMetadata->type & drwav_metadata_type_list_all_info_strings) {
                const char* pID = NULL;

                switch (pMetadata->type) {
                    case drwav_metadata_type_list_info_software:    pID = "ISFT"
; break;
                    case drwav_metadata_type_list_info_copyright:   pID = "ICOP"
; break;
                    case drwav_metadata_type_list_info_title:       pID = "INAM"
; break;
                    case drwav_metadata_type_list_info_artist:      pID = "IART"
; break;
                    case drwav_metadata_type_list_info_comment:     pID = "ICMT"
; break;
                    case drwav_metadata_type_list_info_date:        pID = "ICRD"
; break;
                    case drwav_metadata_type_list_info_genre:       pID = "IGNR"
; break;
                    case drwav_metadata_type_list_info_album:       pID = "IPRD"
; break;
                    case drwav_metadata_type_list_info_tracknumber: pID = "ITRK"
; break;
                    default: break;
                }

                DRWAV_ASSERT(pID != NULL);

                if (pMetadata->data.infoText.stringLength) {
                    subchunkSize = pMetadata->data.infoText.stringLength + 1;
                    bytesWritten += drwav__write_or_count(pWav, pID, 4);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, subc
hunkSize);
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
infoText.pString, pMetadata->data.infoText.stringLength);
                    bytesWritten += drwav__write_or_count_byte(pWav, '\0');
                }
            } else if (pMetadata->type == drwav_metadata_type_unknown && pMetada
ta->data.unknown.chunkLocation == drwav_metadata_location_inside_info_list) {
                if (pMetadata->data.unknown.dataSizeInBytes) {
                    subchunkSize = pMetadata->data.unknown.dataSizeInBytes;

                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
unknown.id, 4);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.unknown.dataSizeInBytes);
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
unknown.pData, subchunkSize);
                }
            }

            if ((subchunkSize % 2) != 0) {
                bytesWritten += drwav__write_or_count_byte(pWav, 0);
            }
        }
    }

    if (hasListAdtl) {
        drwav_uint32 chunkSize = 4; /* start with 4 bytes for "adtl" */

        for (iMetadata = 0; iMetadata < metadataCount; ++iMetadata) {
            drwav_metadata* pMetadata = &pMetadatas[iMetadata];

            switch (pMetadata->type)
            {
                case drwav_metadata_type_list_label:
                case drwav_metadata_type_list_note:
                {
                    chunkSize += 8; /* for id and chunk size */
                    chunkSize += DRWAV_LIST_LABEL_OR_NOTE_BYTES;

                    if (pMetadata->data.labelOrNote.stringLength > 0) {
                        chunkSize += pMetadata->data.labelOrNote.stringLength +
1;
                    }
                } break;

                case drwav_metadata_type_list_labelled_cue_region:
                {
                    chunkSize += 8; /* for id and chunk size */
                    chunkSize += DRWAV_LIST_LABELLED_TEXT_BYTES;

                    if (pMetadata->data.labelledCueRegion.stringLength > 0) {
                        chunkSize += pMetadata->data.labelledCueRegion.stringLen
gth + 1;
                    }
                } break;

                case drwav_metadata_type_unknown:
                {
                    if (pMetadata->data.unknown.chunkLocation == drwav_metadata_
location_inside_adtl_list) {
                        chunkSize += 8; /* for id and chunk size */
                        chunkSize += pMetadata->data.unknown.dataSizeInBytes;
                    }
                } break;

                default: break;
            }

            if ((chunkSize % 2) != 0) {
                chunkSize += 1;
            }
        }

        bytesWritten += drwav__write_or_count(pWav, "LIST", 4);
        bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, chunkSize);
        bytesWritten += drwav__write_or_count(pWav, "adtl", 4);

        for (iMetadata = 0; iMetadata < metadataCount; ++iMetadata) {
            drwav_metadata* pMetadata = &pMetadatas[iMetadata];
            drwav_uint32 subchunkSize = 0;

            switch (pMetadata->type)
            {
                case drwav_metadata_type_list_label:
                case drwav_metadata_type_list_note:
                {
                    if (pMetadata->data.labelOrNote.stringLength > 0) {
                        const char *pID = NULL;

                        if (pMetadata->type == drwav_metadata_type_list_label) {
                            pID = "labl";
                        }
                        else if (pMetadata->type == drwav_metadata_type_list_not
e) {
                            pID = "note";
                        }

                        DRWAV_ASSERT(pID != NULL);
                        DRWAV_ASSERT(pMetadata->data.labelOrNote.pString != NULL
);

                        subchunkSize = DRWAV_LIST_LABEL_OR_NOTE_BYTES;

                        bytesWritten += drwav__write_or_count(pWav, pID, 4);
                        subchunkSize += pMetadata->data.labelOrNote.stringLength
 + 1;
                        bytesWritten += drwav__write_or_count_u32ne_to_le(pWav,
subchunkSize);

                        bytesWritten += drwav__write_or_count_u32ne_to_le(pWav,
pMetadata->data.labelOrNote.cuePointId);
                        bytesWritten += drwav__write_or_count(pWav, pMetadata->d
ata.labelOrNote.pString, pMetadata->data.labelOrNote.stringLength);
                        bytesWritten += drwav__write_or_count_byte(pWav, '\0');
                    }
                } break;

                case drwav_metadata_type_list_labelled_cue_region:
                {
                    subchunkSize = DRWAV_LIST_LABELLED_TEXT_BYTES;

                    bytesWritten += drwav__write_or_count(pWav, "ltxt", 4);
                    if (pMetadata->data.labelledCueRegion.stringLength > 0) {
                        subchunkSize += pMetadata->data.labelledCueRegion.string
Length + 1;
                    }
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, subc
hunkSize);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.labelledCueRegion.cuePointId);
                    bytesWritten += drwav__write_or_count_u32ne_to_le(pWav, pMet
adata->data.labelledCueRegion.sampleLength);
                    bytesWritten += drwav__write_or_count(pWav, pMetadata->data.
labelledCueRegion.purposeId, 4);
                    bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMet
adata->data.labelledCueRegion.country);
                    bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMet
adata->data.labelledCueRegion.language);
                    bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMet
adata->data.labelledCueRegion.dialect);
                    bytesWritten += drwav__write_or_count_u16ne_to_le(pWav, pMet
adata->data.labelledCueRegion.codePage);

                    if (pMetadata->data.labelledCueRegion.stringLength > 0) {
                        DRWAV_ASSERT(pMetadata->data.labelledCueRegion.pString !
= NULL);

                        bytesWritten += drwav__write_or_count(pWav, pMetadata->d
ata.labelledCueRegion.pString, pMetadata->data.labelledCueRegion.stringLength);
                        bytesWritten += drwav__write_or_count_byte(pWav, '\0');
                    }
                } break;

                case drwav_metadata_type_unknown:
                {
                    if (pMetadata->data.unknown.chunkLocation == drwav_metadata_
location_inside_adtl_list) {
                        subchunkSize = pMetadata->data.unknown.dataSizeInBytes;

                        DRWAV_ASSERT(pMetadata->data.unknown.pData != NULL);
                        bytesWritten += drwav__write_or_count(pWav, pMetadata->d
ata.unknown.id, 4);
                        bytesWritten += drwav__write_or_count_u32ne_to_le(pWav,
subchunkSize);
                        bytesWritten += drwav__write_or_count(pWav, pMetadata->d
ata.unknown.pData, subchunkSize);
                    }
                } break;

                default: break;
            }

            if ((subchunkSize % 2) != 0) {
                bytesWritten += drwav__write_or_count_byte(pWav, 0);
            }
        }
    }

    DRWAV_ASSERT((bytesWritten % 2) == 0);

    return bytesWritten;
}

DRWAV_PRIVATE drwav_uint32 drwav__riff_chunk_size_riff(drwav_uint64 dataChunkSiz
e, drwav_metadata* pMetadata, drwav_uint32 metadataCount)
{
    drwav_uint64 chunkSize = 4 + 24 + (drwav_uint64)drwav__write_or_count_metada
ta(NULL, pMetadata, metadataCount) + 8 + dataChunkSize + drwav__chunk_padding_si
ze_riff(dataChunkSize); /* 4 = "WAVE". 24 = "fmt " chunk. 8 = "data" + u32 data
size. */
    if (chunkSize > 0xFFFFFFFFUL) {
        chunkSize = 0xFFFFFFFFUL;
    }

    return (drwav_uint32)chunkSize; /* Safe cast due to the clamp above. */
}

DRWAV_PRIVATE drwav_uint32 drwav__data_chunk_size_riff(drwav_uint64 dataChunkSiz
e)
{
    if (dataChunkSize <= 0xFFFFFFFFUL) {
        return (drwav_uint32)dataChunkSize;
    } else {
        return 0xFFFFFFFFUL;
    }
}

DRWAV_PRIVATE drwav_uint64 drwav__riff_chunk_size_w64(drwav_uint64 dataChunkSize
)
{
    drwav_uint64 dataSubchunkPaddingSize = drwav__chunk_padding_size_w64(dataChu
nkSize);

    return 80 + 24 + dataChunkSize + dataSubchunkPaddingSize;   /* +24 because W
64 includes the size of the GUID and size fields. */
}

DRWAV_PRIVATE drwav_uint64 drwav__data_chunk_size_w64(drwav_uint64 dataChunkSize
)
{
    return 24 + dataChunkSize;        /* +24 because W64 includes the size of th
e GUID and size fields. */
}

DRWAV_PRIVATE drwav_uint64 drwav__riff_chunk_size_rf64(drwav_uint64 dataChunkSiz
e, drwav_metadata *metadata, drwav_uint32 numMetadata)
{
    drwav_uint64 chunkSize = 4 + 36 + 24 + (drwav_uint64)drwav__write_or_count_m
etadata(NULL, metadata, numMetadata) + 8 + dataChunkSize + drwav__chunk_padding_
size_riff(dataChunkSize); /* 4 = "WAVE". 36 = "ds64" chunk. 24 = "fmt " chunk. 8
 = "data" + u32 data size. */
    if (chunkSize > 0xFFFFFFFFUL) {
        chunkSize = 0xFFFFFFFFUL;
    }

    return chunkSize;
}

DRWAV_PRIVATE drwav_uint64 drwav__data_chunk_size_rf64(drwav_uint64 dataChunkSiz
e)
{
    return dataChunkSize;
}



DRWAV_PRIVATE drwav_bool32 drwav_preinit_write(drwav* pWav, const drwav_data_for
mat* pFormat, drwav_bool32 isSequential, drwav_write_proc onWrite, drwav_seek_pr
oc onSeek, void* pUserData, const drwav_allocation_callbacks* pAllocationCallbac
ks)
{
    if (pWav == NULL || onWrite == NULL) {
        return DRWAV_FALSE;
    }

    if (!isSequential && onSeek == NULL) {
        return DRWAV_FALSE; /* <-- onSeek is required when in non-sequential mod
e. */
    }

    /* Not currently supporting compressed formats. Will need to add support for
 the "fact" chunk before we enable this. */
    if (pFormat->format == DR_WAVE_FORMAT_EXTENSIBLE) {
        return DRWAV_FALSE;
    }
    if (pFormat->format == DR_WAVE_FORMAT_ADPCM || pFormat->format == DR_WAVE_FO
RMAT_DVI_ADPCM) {
        return DRWAV_FALSE;
    }

    DRWAV_ZERO_MEMORY(pWav, sizeof(*pWav));
    pWav->onWrite   = onWrite;
    pWav->onSeek    = onSeek;
    pWav->pUserData = pUserData;
    pWav->allocationCallbacks = drwav_copy_allocation_callbacks_or_defaults(pAll
ocationCallbacks);

    if (pWav->allocationCallbacks.onFree == NULL || (pWav->allocationCallbacks.o
nMalloc == NULL && pWav->allocationCallbacks.onRealloc == NULL)) {
        return DRWAV_FALSE;    /* Invalid allocation callbacks. */
    }

    pWav->fmt.formatTag = (drwav_uint16)pFormat->format;
    pWav->fmt.channels = (drwav_uint16)pFormat->channels;
    pWav->fmt.sampleRate = pFormat->sampleRate;
    pWav->fmt.avgBytesPerSec = (drwav_uint32)((pFormat->bitsPerSample * pFormat-
>sampleRate * pFormat->channels) / 8);
    pWav->fmt.blockAlign = (drwav_uint16)((pFormat->channels * pFormat->bitsPerS
ample) / 8);
    pWav->fmt.bitsPerSample = (drwav_uint16)pFormat->bitsPerSample;
    pWav->fmt.extendedSize = 0;
    pWav->isSequentialWrite = isSequential;

    return DRWAV_TRUE;
}


DRWAV_PRIVATE drwav_bool32 drwav_init_write__internal(drwav* pWav, const drwav_d
ata_format* pFormat, drwav_uint64 totalSampleCount)
{
    /* The function assumes drwav_preinit_write() was called beforehand. */

    size_t runningPos = 0;
    drwav_uint64 initialDataChunkSize = 0;
    drwav_uint64 chunkSizeFMT;

    /*
    The initial values for the "RIFF" and "data" chunks depends on whether or no
t we are initializing in sequential mode or not. In
    sequential mode we set this to its final values straight away since they can
 be calculated from the total sample count. In non-
    sequential mode we initialize it all to zero and fill it out in drwav_uninit
() using a backwards seek.
    */
    if (pWav->isSequentialWrite) {
        initialDataChunkSize = (totalSampleCount * pWav->fmt.bitsPerSample) / 8;

        /*
        The RIFF container has a limit on the number of samples. drwav is not al
lowing this. There's no practical limits for Wave64
        so for the sake of simplicity I'm not doing any validation for that.
        */
        if (pFormat->container == drwav_container_riff) {
            if (initialDataChunkSize > (0xFFFFFFFFUL - 36)) {
                return DRWAV_FALSE; /* Not enough room to store every sample. */
            }
        }
    }

    pWav->dataChunkDataSizeTargetWrite = initialDataChunkSize;


    /* "RIFF" chunk. */
    if (pFormat->container == drwav_container_riff) {
        drwav_uint32 chunkSizeRIFF = 28 + (drwav_uint32)initialDataChunkSize;
/* +28 = "WAVE" + [sizeof "fmt " chunk] */
        runningPos += drwav__write(pWav, "RIFF", 4);
        runningPos += drwav__write_u32ne_to_le(pWav, chunkSizeRIFF);
        runningPos += drwav__write(pWav, "WAVE", 4);
    } else if (pFormat->container == drwav_container_w64) {
        drwav_uint64 chunkSizeRIFF = 80 + 24 + initialDataChunkSize;
/* +24 because W64 includes the size of the GUID and size fields. */
        runningPos += drwav__write(pWav, drwavGUID_W64_RIFF, 16);
        runningPos += drwav__write_u64ne_to_le(pWav, chunkSizeRIFF);
        runningPos += drwav__write(pWav, drwavGUID_W64_WAVE, 16);
    } else if (pFormat->container == drwav_container_rf64) {
        runningPos += drwav__write(pWav, "RF64", 4);
        runningPos += drwav__write_u32ne_to_le(pWav, 0xFFFFFFFF);
/* Always 0xFFFFFFFF for RF64. Set to a proper value in the "ds64" chunk. */
        runningPos += drwav__write(pWav, "WAVE", 4);
    } else {
        return DRWAV_FALSE; /* Container not supported for writing. */
    }


    /* "ds64" chunk (RF64 only). */
    if (pFormat->container == drwav_container_rf64) {
        drwav_uint32 initialds64ChunkSize = 28;
/* 28 = [Size of RIFF (8 bytes)] + [Size of DATA (8 bytes)] + [Sample Count (8 b
ytes)] + [Table Length (4 bytes)]. Table length always set to 0. */
        drwav_uint64 initialRiffChunkSize = 8 + initialds64ChunkSize + initialDa
taChunkSize;    /* +8 for the ds64 header. */

        runningPos += drwav__write(pWav, "ds64", 4);
        runningPos += drwav__write_u32ne_to_le(pWav, initialds64ChunkSize);
/* Size of ds64. */
        runningPos += drwav__write_u64ne_to_le(pWav, initialRiffChunkSize);
/* Size of RIFF. Set to true value at the end. */
        runningPos += drwav__write_u64ne_to_le(pWav, initialDataChunkSize);
/* Size of DATA. Set to true value at the end. */
        runningPos += drwav__write_u64ne_to_le(pWav, totalSampleCount);
/* Sample count. */
        runningPos += drwav__write_u32ne_to_le(pWav, 0);
/* Table length. Always set to zero in our case since we're not doing any other
chunks than "DATA". */
    }


    /* "fmt " chunk. */
    if (pFormat->container == drwav_container_riff || pFormat->container == drwa
v_container_rf64) {
        chunkSizeFMT = 16;
        runningPos += drwav__write(pWav, "fmt ", 4);
        runningPos += drwav__write_u32ne_to_le(pWav, (drwav_uint32)chunkSizeFMT)
;
    } else if (pFormat->container == drwav_container_w64) {
        chunkSizeFMT = 40;
        runningPos += drwav__write(pWav, drwavGUID_W64_FMT, 16);
        runningPos += drwav__write_u64ne_to_le(pWav, chunkSizeFMT);
    }

    runningPos += drwav__write_u16ne_to_le(pWav, pWav->fmt.formatTag);
    runningPos += drwav__write_u16ne_to_le(pWav, pWav->fmt.channels);
    runningPos += drwav__write_u32ne_to_le(pWav, pWav->fmt.sampleRate);
    runningPos += drwav__write_u32ne_to_le(pWav, pWav->fmt.avgBytesPerSec);
    runningPos += drwav__write_u16ne_to_le(pWav, pWav->fmt.blockAlign);
    runningPos += drwav__write_u16ne_to_le(pWav, pWav->fmt.bitsPerSample);

    /* TODO: is a 'fact' chunk required for DR_WAVE_FORMAT_IEEE_FLOAT? */

    if (!pWav->isSequentialWrite && pWav->pMetadata != NULL && pWav->metadataCou
nt > 0 && (pFormat->container == drwav_container_riff || pFormat->container == d
rwav_container_rf64)) {
        runningPos += drwav__write_or_count_metadata(pWav, pWav->pMetadata, pWav
->metadataCount);
    }

    pWav->dataChunkDataPos = runningPos;

    /* "data" chunk. */
    if (pFormat->container == drwav_container_riff) {
        drwav_uint32 chunkSizeDATA = (drwav_uint32)initialDataChunkSize;
        runningPos += drwav__write(pWav, "data", 4);
        runningPos += drwav__write_u32ne_to_le(pWav, chunkSizeDATA);
    } else if (pFormat->container == drwav_container_w64) {
        drwav_uint64 chunkSizeDATA = 24 + initialDataChunkSize;     /* +24 becau
se W64 includes the size of the GUID and size fields. */
        runningPos += drwav__write(pWav, drwavGUID_W64_DATA, 16);
        runningPos += drwav__write_u64ne_to_le(pWav, chunkSizeDATA);
    } else if (pFormat->container == drwav_container_rf64) {
        runningPos += drwav__write(pWav, "data", 4);
        runningPos += drwav__write_u32ne_to_le(pWav, 0xFFFFFFFF);   /* Always se
t to 0xFFFFFFFF for RF64. The true size of the data chunk is specified in the ds
64 chunk. */
    }

    /* Set some properties for the client's convenience. */
    pWav->container = pFormat->container;
    pWav->channels = (drwav_uint16)pFormat->channels;
    pWav->sampleRate = pFormat->sampleRate;
    pWav->bitsPerSample = (drwav_uint16)pFormat->bitsPerSample;
    pWav->translatedFormatTag = (drwav_uint16)pFormat->format;
    pWav->dataChunkDataPos = runningPos;

    return DRWAV_TRUE;
}


DRWAV_API drwav_bool32 drwav_init_write(drwav* pWav, const drwav_data_format* pF
ormat, drwav_write_proc onWrite, drwav_seek_proc onSeek, void* pUserData, const
drwav_allocation_callbacks* pAllocationCallbacks)
{
    if (!drwav_preinit_write(pWav, pFormat, DRWAV_FALSE, onWrite, onSeek, pUserD
ata, pAllocationCallbacks)) {
        return DRWAV_FALSE;
    }

    return drwav_init_write__internal(pWav, pFormat, 0);               /* DRWAV_
FALSE = Not Sequential */
}

DRWAV_API drwav_bool32 drwav_init_write_sequential(drwav* pWav, const drwav_data
_format* pFormat, dr
