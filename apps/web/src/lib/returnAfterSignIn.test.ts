import { afterEach, describe, expect, it } from 'vitest'
import { saveReturnPath, takeReturnPath } from './returnAfterSignIn'

describe('returnAfterSignIn', () => {
  afterEach(() => sessionStorage.clear())

  it('gives back a shopping list path once', () => {
    saveReturnPath('/list/01a1e8d4-33bc-75f7-b3a6-bc58ce99b6d4')
    expect(takeReturnPath()).toBe('/list/01a1e8d4-33bc-75f7-b3a6-bc58ce99b6d4')
    expect(takeReturnPath()).toBeNull()
  })

  it('never returns to another site or an arbitrary path', () => {
    for (const path of [
      'https://evil.example/list/x',
      '//evil.example',
      '/account',
      '/list/../oauth2',
    ]) {
      saveReturnPath(path)
      expect(takeReturnPath()).toBeNull()
    }
    sessionStorage.setItem('kitchen.returnAfterSignIn', '//evil.example/list/x')
    expect(takeReturnPath()).toBeNull()
  })
})
