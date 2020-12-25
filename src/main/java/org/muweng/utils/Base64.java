package org.muweng.utils;

/**
 * @author: muweng
 * @date: 2020/12/25 18:32
 * @description: 手工实现base64加解密.
 */
public class Base64 {

    /**
     * 基于RFC2045
     */
    private static final char[] RFC2045 = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
    };

    /**
     * RFC2045 base64加密
     * @param bytes
     * @return
     */
    public static String encodeToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        //8位二进制->6位二进制
        int idx1, idx2, idx3, idx4;
        int srcLength = bytes.length;
        int count = srcLength / 3;

        // 0000_0000 0000_0000 0000_0000
        // (**0000_00) (**00 0000) ( **_0000 00) (** 00_0000)
        // **代表补00
        for (int i = 0; i < count; i++) {
            //移位 8—>6
            idx1 = bytes[i * 3] >> 2;
            //高位补00
            idx1 = idx1 & 0x3F;

            //获取第一字节的尾部2位
            idx2 = bytes[i * 3] & 0x03;
            //扩充位数到6位
            idx2 = idx2 << 4;
            //取第二字节的头4位和第一字节的尾2位合并 （第一字节头2位+第二字节的头4位）
            idx2 = idx2 | ((bytes[i * 3 + 1] & 0xF0) >> 4);
            //高位补00
            idx2 = idx2 & 0x3F;

            //获取第二字节的尾部4位
            idx3 = bytes[i * 3 + 1] & 0x0F;
            //扩充位数到6位
            idx3 = idx3 << 2;
            //取第三字节的头2位和第二字节的尾4位合并 （第二字节尾4位+第三字节的头2位）
            idx3 = idx3 | ((bytes[i * 3 + 2] & 0xC0) >> 6);
            //高位补00
            idx3 = idx3 & 0x3F;

            //取第三字节的尾6位,高位补00
            idx4 = bytes[i * 3 + 2] & 0x3F;

            stringBuilder
                    .append(RFC2045[idx1])
                    .append(RFC2045[idx2])
                    .append(RFC2045[idx3])
                    .append(RFC2045[idx4]);
        }

        //获取源字符长度中最大的3倍数
        int remainPos = srcLength / 3;
        remainPos = remainPos * 3;

        switch (srcLength % 3){
            case 1:
                //0000_0000
                //(**0000_00) (**00****)
                //不够4字节  补==
                idx1 = bytes[remainPos] >> 2;
                idx1 = idx1 & 0x3F;
                idx2 = bytes[remainPos] & 0x03;
                idx2 = idx2 << 4;
                idx2 = idx2 & 0x3F;
                stringBuilder
                        .append(RFC2045[idx1])
                        .append(RFC2045[idx2])
                        .append('=')
                        .append('=');
                break;
            case 2:
                //0000_0000 0000_0000
                //(**0000_00) (**00 0000) (**00_0000)
                //不够4字节  补=
                idx1 = bytes[remainPos] >> 2;
                idx1 = idx1 & 0x3F;

                idx2 = bytes[remainPos] & 0x03;
                idx2 = idx2 << 4;
                idx2 = idx2 | ((bytes[remainPos+1] & 0xF0) >> 4);
                idx2 = idx2 & 0x3F;

                idx3 = bytes[remainPos+1] & 0x0F;
                idx3 = idx3 << 2;
                idx3 = idx3 & 0x3F;
                stringBuilder.append(RFC2045[idx1])
                        .append(RFC2045[idx2])
                        .append(RFC2045[idx3])
                        .append('=');
                break;
            default:
                break;
        }
        return stringBuilder.toString();
    }

    public static void main(String[] args) {
        System.out.println(encodeToString(new byte[]{0, 0, 0,0}));
        System.out.println(java.util.Base64.getEncoder().encodeToString(new byte[]{0, 0, 0,0}));
    }
}
