DESCRIPTION = "Linux Kernel"
HOMEPAGE = "www.kernel.org"
LICENSE = "GPLv2"
SECTION = "kernel"
DEPENDS = ""

LIC_FILES_CHKSUM = "file://COPYING;md5=bbea815ee2795b2f4230826c0c6b8814"

inherit kernel siteinfo

LEDGE_KVERSION = "5.1-rc5"

# Stable kernel URL
# SRC_URI = "https://cdn.kernel.org/pub/linux/kernel/v4.x/linux-${LEDGE_KVERSION}.tar.xz;name=kernel"

# RC kernel URL
SRC_URI = "https://git.kernel.org/torvalds/t/linux-${LEDGE_KVERSION}.tar.gz;name=kernel"
SRC_URI[kernel.md5sum] = "fb95f2fd96a41ca70021058fe724d184"
SRC_URI[kernel.sha256sum] = "8778d4872556c7eb0e2529443b965113b022b62649854188085de68f9c522c9c"

SRC_URI_append_ledge-stm32mp157c-dk2 = " file://0001-stm32mp1-support-of-board-dk1-and-dk2.patch "

PV = "mainline-5.1-rc5"
S = "${WORKDIR}/linux-5.1-rc5"

KERNEL_DEFCONFIG = "defconfig"
KERNEL_CONFIG_FRAGMENTS_append = " ${WORKDIR}/fragment-02-systemd.config "
KERNEL_CONFIG_FRAGMENTS_append = " ${WORKDIR}/fragment-10-ledge.config "
KERNEL_CONFIG_COMMAND = "oe_runmake -C ${S} O=${B} ${KERNEL_DEFCONFIG}"

SRC_URI_append = " file://fragment-02-systemd.config "
SRC_URI_append = " file://fragment-10-ledge.config "

COMPATIBLE_MACHINE = "(ledge-espressobin|ledge-stm32mp157c-dk2|ledge-qemux86-64|ledge-qemuarm|ledge-qemuarm64)"

do_configure() {
    touch ${B}/.scmversion ${S}/.scmversion

    if [ ! -z ${KERNEL_DEFCONFIG} ]; then
        bbnote "Kernel customized: configuration of linux by using DEFCONFIG: ${KERNEL_DEFCONFIG}"
        oe_runmake ${PARALLEL_MAKE} -C ${S} O=${B} CC="${KERNEL_CC}" LD="${KERNEL_LD}" ${KERNEL_DEFCONFIG}
    else
        if [ ! -z ${KERNEL_EXTERNAL_DEFCONFIG} ]; then
            bbnote "Kernel customized: configuration of linux by using external DEFCONFIG"
            install -m 0644 ${WORKDIR}/${KERNEL_EXTERNAL_DEFCONFIG} ${B}/.config
            oe_runmake -C ${S} O=${B} CC="${KERNEL_CC}" LD="${KERNEL_LD}" oldconfig
        else
            bbwarn "You must specify KERNEL_DEFCONFIG or KERNEL_EXTERNAL_DEFCONFIG"
            die "NO DEFCONFIG SPECIFIED"
        fi
    fi
    if [ ! -z "${KERNEL_CONFIG_FRAGMENTS}" ]; then
        for f in ${KERNEL_CONFIG_FRAGMENTS}
        do
            # Check if the config fragment was copied into the WORKDIR from
            # the OE meta data
            if [ ! -e "$f" ]; then
                echo "Could not find kernel config fragment $f"
                exit 1
            fi
        done

        bbnote "${S}/scripts/kconfig/merge_config.sh -m -r -O ${B} ${B}/.config ${KERNEL_CONFIG_FRAGMENTS} 1>&2"
        # Now that all the fragments are located merge them.
        (${S}/scripts/kconfig/merge_config.sh -m -r -O ${B} ${B}/.config ${KERNEL_CONFIG_FRAGMENTS} 1>&2 )
    fi

    yes '' | oe_runmake -C ${S} O=${B} CC="${KERNEL_CC}" LD="${KERNEL_LD}" oldconfig
    #oe_runmake -C ${S} O=${B} savedefconfig && cp ${B}/defconfig ${WORKDIR}/defconfig.saved

    bbplain "Saving defconfig to:\n${B}/defconfig"
    oe_runmake -C ${B} savedefconfig
    cp -a ${B}/defconfig ${DEPLOYDIR}
}

# -----------------------------------------------------
#             EFI
# Determine the target arch for kernel as EFI firmware
python __anonymous () {
    import re
    target = d.getVar('TARGET_ARCH')
    if target == "x86_64":
        kernel_efi_image = "bootx64.efi"
    elif re.match('i.86', target):
        kernel_efi_image = "bootia32.efi"
    elif re.match('aarch64', target):
        kernel_efi_image = "bootaa64.efi"
    elif re.match('arm', target):
        kernel_efi_image = "bootarm.efi"
    else:
        raise bb.parse.SkipRecipe("kernel efi is incompatible with target %s" % target)
    d.setVar("KERNEL_EFI_IMAGE", kernel_efi_image)
}

do_install_append() {
#    if [ "${@bb.utils.contains('DISTRO_FEATURES', 'efi', '1', '0', d)}" = "1" ]; then
        for t in ${KERNEL_IMAGETYPE} ${KERNEL_ALT_IMAGETYPE}; do
            if [ "$t" = "zImage" ]; then
                install -d ${D}/boot/efi/boot
                ln -s ../../zImage ${D}/boot/efi/boot/${KERNEL_EFI_IMAGE}
            fi
        done

#    fi
}
python __anonymous () {
    types = d.getVar('KERNEL_IMAGETYPES') or ""
    kname = d.getVar('KERNEL_PACKAGE_NAME') or "kernel"
    for type in types.split():
        typelower = type.lower()
        if typelower == 'zimage':
            d.appendVar('FILES_' + kname + '-image-' + typelower, ' /boot/efi/boot ')
}