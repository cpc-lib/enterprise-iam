import http from './http'
import type { WebAuthnCredentialItem } from '@/types/identity'

export interface WebAuthnRegistrationOptions {
  challengeId: string
  challenge: string
  rpId: string
  rpName: string
  userId: string
  username: string
  pubKeyCredParams: number[]
  timeout: number
}

export interface WebAuthnAuthenticationOptions {
  challengeId: string
  challenge: string
  rpId: string
  timeout: number
  allowedCredentialIds: string[]
}

export interface WebAuthnAuthenticationResult {
  authenticated: boolean
  tenantId: string
  userId: string
  credentialId: string
  deviceName: string
  signCount: number
}

export async function createWebAuthnRegistrationOptions(params: {
  tenantId: string
  userId: string
  username: string
  rpId?: string
}): Promise<WebAuthnRegistrationOptions> {
  const { data } = await http.post<WebAuthnRegistrationOptions>('/api/v1/auth/webauthn/register/options', {
    tenantId: params.tenantId,
    userId: params.userId,
    username: params.username,
    rpId: params.rpId ?? window.location.hostname,
  })
  return data
}

export async function verifyWebAuthnRegistration(params: {
  tenantId: string
  userId: string
  challengeId: string
  credentialId: string
  publicKeyCose: string
  initialSignCount?: number
  transports?: string
  deviceName: string
}): Promise<WebAuthnCredentialItem> {
  const { data } = await http.post<WebAuthnCredentialItem>('/api/v1/auth/webauthn/register/verify', params)
  return data
}

export async function createWebAuthnLoginOptions(params: {
  tenantId: string
  username?: string
  rpId?: string
}): Promise<WebAuthnAuthenticationOptions> {
  const { data } = await http.post<WebAuthnAuthenticationOptions>('/api/v1/auth/webauthn/login/options', {
    tenantId: params.tenantId,
    username: params.username,
    rpId: params.rpId ?? window.location.hostname,
  })
  return data
}

export async function verifyWebAuthnLogin(params: {
  tenantId: string
  challengeId: string
  credentialId: string
  authenticatorSignCount: number
  clientDataJsonB64: string
  signatureB64: string
}): Promise<WebAuthnAuthenticationResult> {
  const { data } = await http.post<WebAuthnAuthenticationResult>('/api/v1/auth/webauthn/login/verify', params)
  return data
}

export async function listWebAuthnCredentials(tenantId: string, userId: string): Promise<WebAuthnCredentialItem[]> {
  const { data } = await http.get<WebAuthnCredentialItem[]>('/api/v1/auth/webauthn/credentials', {
    params: { tenantId, userId },
  })
  return data
}

export async function revokeWebAuthnCredential(id: string, tenantId: string, userId: string): Promise<void> {
  await http.delete(`/api/v1/auth/webauthn/credentials/${id}`, {
    params: { tenantId, userId },
  })
}
